package providers.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import providers.ProviderDetailsRepository

import middleware.BaseController

object QueryVmCreationController extends BaseController {

  def queryVmCreation: Route = path("query") {
    post {
      entity(as[Map[String, String]]) { request =>
        val providerId = request.get("provider_id")
        val vcpus = request.get("vcpus").map(_.toInt)
        val ram = request.get("ram").map(_.toInt)
        val storage = request.get("storage").map(_.toInt)

        if (providerId.isEmpty) {
          complete((400, Map("error" -> "Provider ID is required").asJson))
        } else {
          val result: IO[Either[String, Boolean]] = ProviderDetailsRepository.canCreateVm(providerId.get, vcpus, ram, storage)

          onComplete(result.attempt.unsafeToFuture()) {
            case scala.util.Success(Right(Right(true))) =>
              complete((200, Map("can_create" -> true).asJson))
            case scala.util.Success(Right(Right(false))) =>
              complete((200, Map("can_create" -> false).asJson))
            case scala.util.Success(Right(Left(error))) =>
              complete((500, Map("error" -> error).asJson))
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
