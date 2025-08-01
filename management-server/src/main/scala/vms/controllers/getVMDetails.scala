package vms.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import middleware.BaseController
import vms.VmDetailsRepository
import vms.VmCrudService

object VMDetailsController extends BaseController {

  implicit val eitherEncoder: Encoder[Either[Throwable, List[(String, String, String, String, String, String, String, String, String, java.time.LocalDateTime)]]] =
    Encoder.instance {
      case Right(list) => list.asJson
      case Left(error) => Json.obj("error" -> Json.fromString(error.getMessage))
    }

  def allActiveVms: Route = path("allActiveVms") {
    get {
      uiLoginRequired { user =>
        val userId = user.get("user_id")

        if (userId.isEmpty) {
          complete((400, Map("error" -> "User ID is required").asJson))
        } else {
          val result = VmDetailsRepository.getAllActiveVms(userId.get)

          onComplete(result.attempt.unsafeToFuture()) {
            case scala.util.Success(Right(response)) =>
              complete((200, Map("active_vms" -> response).asJson))
            case scala.util.Success(Left(error)) =>
              complete((500, Map("error" -> error.getMessage).asJson))
            case scala.util.Failure(exception) =>
              complete((500, Map("error" -> exception.getMessage).asJson))
          }
        }
      }
    }
  }

  def allVms: Route = path("allVms") {
    get {
      uiLoginRequired { user =>
        val userId = user.get("user_id")

        if (userId.isEmpty) {
          complete((400, Map("error" -> "User ID is required").asJson))
        } else {
          parameter("vm_name".?) { vmNameOpt =>
            val result = vmNameOpt match {
              case Some(vmName) if vmName.nonEmpty =>
                VmDetailsRepository.getAllVmsFiltered(userId.get, vmName)
              case _ =>
                VmDetailsRepository.getAllVms(userId.get)
            }

            onComplete(result.attempt.unsafeToFuture()) {
              case scala.util.Success(Right(response)) =>
                complete((200, Map("all_vms" -> response.asJson)))
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

  def startVm: Route = path("start") {
    post {
      uiLoginRequired { user =>
        entity(as[Map[String, String]]) { request =>
          val userId = user.get("user_id")
          val vmId = request.get("vm_id")
          val providerId = request.get("provider_id")

          if (vmId.isEmpty || providerId.isEmpty) {
            complete((400, Map("error" -> "VM ID and Provider ID are required").asJson))
          } else {
            val result = VmCrudService.activateVm(providerId.get, vmId.get, userId.get)

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

  def stopVm: Route = path("stop") {
    post {
      uiLoginRequired { user =>
        entity(as[Map[String, String]]) { request =>
          val userId = user.get("user_id")
          val vmId = request.get("vm_id")
          val providerId = request.get("provider_id")

          if (vmId.isEmpty || providerId.isEmpty) {
            complete((400, Map("error" -> "VM ID and Provider ID are required").asJson))
          } else {
            val result = VmCrudService.deactivateVm(providerId.get, vmId.get, userId.get)

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

  def removeVm: Route = path("remove") {
    post {
      uiLoginRequired { user =>
        entity(as[Map[String, String]]) { request =>
          val userId = user.get("user_id")
          val vmId = request.get("vm_id")
          val providerId = request.get("provider_id")

          if (vmId.isEmpty || providerId.isEmpty) {
            complete((400, Map("error" -> "VM ID and Provider ID are required").asJson))
          } else {
            val result = VmCrudService.deleteVm(providerId.get, vmId.get, userId.get)

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

  def forceRemoveVm: Route = path("forceRemove") {
    post {
      uiLoginRequired { user =>
        entity(as[Map[String, String]]) { request =>
          val userId = user.get("user_id")
          val vmId = request.get("vm_id")
          val providerId = request.get("provider_id")

          if (vmId.isEmpty || providerId.isEmpty) {
            complete((400, Map("error" -> "VM ID and Provider ID are required").asJson))
          } else {
            val result = VmCrudService.forceRemoveVm(vmId.get, providerId.get, userId.get)

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
}
