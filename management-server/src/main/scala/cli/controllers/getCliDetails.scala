package cli.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import middleware.BaseController

import vms.VmDetailsRepository
import vms.VmCrudService

object CliDetailsController extends BaseController {

  implicit val eitherEncoder: Encoder[Either[Throwable, String]] =
    Encoder.instance {
      case Right(message) => Json.obj("message" -> Json.fromString(message))
      case Left(error) => Json.obj("error" -> Json.fromString(error.getMessage))
    }

  def startVmCli: Route = path("startCLI") {
    post {
      entity(as[Map[String, String]]) { request =>
        val userId = request.get("user_id")
        val vmName = request.get("vm_name")

        if (userId.isEmpty || vmName.isEmpty) {
          complete((400, Map("error" -> "User ID and VM Name are required").asJson))
        } else {
          val result = for {
            vmDetails <- VmDetailsRepository.getVmDetailsByName(vmName.get, userId.get)
            vmDetails <- IO.fromEither(vmDetails)
            vmId = vmDetails._1
            providerId = vmDetails._2
            response <- VmCrudService.activateVm(providerId, vmId, userId.get)
          } yield response

          onComplete(result.attempt.unsafeToFuture()) {
            case scala.util.Success(Right(response)) =>
              complete((200, response.asJson))
            case scala.util.Success(Left(error)) =>
              complete((500, Map("error" -> error.getMessage).asJson))
            case scala.util.Failure(exception) =>
              complete((500, Map("error" -> exception.getMessage).asJson))
          }
        }
      }
    }
  }

  def stopVmCli: Route = path("stopCLI") {
    post {
      entity(as[Map[String, String]]) { request =>
        val userId = request.get("user_id")
        val vmName = request.get("vm_name")

        if (userId.isEmpty || vmName.isEmpty) {
          complete((400, Map("error" -> "User ID and VM Name are required").asJson))
        } else {
          val result = for {
            vmDetails <- VmDetailsRepository.getVmDetailsByName(vmName.get, userId.get)
            vmDetailsTuple <- IO.fromEither(vmDetails)
            vmId = vmDetailsTuple._1
            providerId = vmDetailsTuple._2
            response <- VmCrudService.deactivateVm(providerId, vmId, userId.get)
          } yield response

          onComplete(result.attempt.unsafeToFuture()) {
            case scala.util.Success(Right(response)) =>
              complete((200, response.asJson))
            case scala.util.Success(Left(error)) =>
              complete((500, Map("error" -> error.getMessage).asJson))
            case scala.util.Failure(exception) =>
              complete((500, Map("error" -> exception.getMessage).asJson))
          }
        }
      }
    }
  }

  def removeVmCli: Route = path("removeCLI") {
    post {
      entity(as[Map[String, String]]) { request =>
        val userId = request.get("user_id")
        val vmName = request.get("vm_name")

        if (userId.isEmpty || vmName.isEmpty) {
          complete((400, Map("error" -> "User ID and VM Name are required").asJson))
        } else {
          val result = VmDetailsRepository.getVmDetailsByName(vmName.get, userId.get)
            .flatMap(vmDetails => IO.fromEither(vmDetails))
            .flatMap { case (vmId, providerId, _) =>
              VmCrudService.deleteVm(providerId, vmId, userId.get)
            }

          onComplete(result.attempt.unsafeToFuture()) {
            case scala.util.Success(Right(response)) =>
              complete((200, response.asJson))
            case scala.util.Success(Left(error)) =>
              complete((500, Map("error" -> error.getMessage).asJson))
            case scala.util.Failure(exception) =>
              complete((500, Map("error" -> exception.getMessage).asJson))
          }
        }
      }
    }
  }

  def forceRemoveVmCli: Route = path("forceRemoveCLI") {
    post {
      entity(as[Map[String, String]]) { request =>
        val userId = request.get("user_id")
        val vmName = request.get("vm_name")

        if (userId.isEmpty || vmName.isEmpty) {
          complete((400, Map("error" -> "User ID and VM Name are required").asJson))
        } else {
          val result = VmDetailsRepository.getVmDetailsByName(vmName.get, userId.get)
            .flatMap(vmDetails => IO.fromEither(vmDetails))
            .flatMap { case (vmId, providerId, _) =>
              VmCrudService.forceRemoveVm(vmId, providerId, userId.get)
            }

          onComplete(result.attempt.unsafeToFuture()) {
            case scala.util.Success(Right(response)) =>
              complete((200, response.asJson))
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