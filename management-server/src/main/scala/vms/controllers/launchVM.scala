package vms.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import providers.ProviderDetailsRepository
import providers.ProviderService
import vms.VmCreationService

object LaunchVmController {

  def launchVm: Route = path("launch") {
    post {
      entity(as[Map[String, String]]) { request =>
        val clientUserId = request.get("user_id")
        val providerId = request.get("provider_id")
        val vcpus = request.get("vcpus").flatMap(_.toIntOption).getOrElse(0)
        val ram = request.get("ram").flatMap(_.toIntOption).getOrElse(0)
        val storage = request.get("storage").flatMap(_.toIntOption).getOrElse(0)
        val vmImageType = request.get("vm_image")

        if (clientUserId.isEmpty || providerId.isEmpty || vmImageType.isEmpty) {
          complete((400, Map("error" -> "Missing required fields").asJson))
        } else {
          val result = for {
            // Query the provider to check if VM creation is possible
            providerDetails <- ProviderDetailsRepository.fetchProviderDetails(providerId.get)
            provider <- IO.fromOption(providerDetails)(new RuntimeException("Provider not found"))
            canCreate <- ProviderService.queryVmCreation(
              provider.providerUrl,
              provider.verificationToken,
              Some(vcpus),
              Some(ram),
              Some(storage)
            )
            _ <- IO.raiseWhen(canCreate.isLeft || !canCreate.exists(identity))(
              new RuntimeException(canCreate.left.getOrElse("Cannot create VM with the given specs"))
            )

            vmCreationResponse <- IO.fromEither(
              VmCreationService
                .createVm(
                  clientUserId.get,
                  providerId.get.toInt,
                  vcpus,
                  ram,
                  storage.toString,
                  vmImageType.get
                )
                .left
                .map(error => new RuntimeException(error))
            )

            _ <- IO(println("VM created successfully"))
          } yield vmCreationResponse

          onComplete(result.attempt.unsafeToFuture()) {
            case scala.util.Success(Right(_)) =>
              complete((200, Map("message" -> "VM is successfully created").asJson))
            case scala.util.Success(Left(error)) =>
              complete((500, Map("error" -> error.getMessage).asJson))
            case scala.util.Failure(exception) =>
              complete((500, Map("error" -> exception.getMessage).asJson))
          }
        }
      }
    }
  }
}
