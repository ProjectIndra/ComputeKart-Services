package providers.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.{Encoder, Json}

import middleware.BaseController

import providers.ProviderDetailsRepository
import vms.VmDetailsRepository
import users.UserDetailsRepository

case class ClientDetails(user_id: String, name: String, email: String)

object ProviderClientDetailsController extends BaseController {

  // Custom encoder for Either[Throwable, List[ClientDetails]]
  implicit val encodeEither: Encoder[Either[Throwable, List[ClientDetails]]] = Encoder.instance {
    case Right(clientDetails) => Json.obj("success" -> clientDetails.asJson)
    case Left(error) => Json.obj("error" -> Json.fromString(error.getMessage))
  }

  def providerClientDetails: Route = path("providerClientDetails") {
    post {
      entity(as[Map[String, String]]) { request =>
        val userId = request.get("user_id")

        if (userId.isEmpty) {
          complete((400, Map("error" -> "User ID is required").asJson))
        } else {
          val result = for {
            vmClients <- VmDetailsRepository.getVmClients(userId.get)
            activeClients <- vmClients match {
              case Right(vmClientList) =>
                vmClientList.foldLeft(IO.pure(List.empty[ClientDetails])) { (accIO, vmClient) =>
                  val (clientUserId, vmId) = vmClient

                  for {
                    acc <- accIO
                    isActive <- VmDetailsRepository.isVmActive(vmId, clientUserId)
                    clientDetails <- isActive match {
                      case Right(true) =>
                        UserDetailsRepository.getClientDetails(clientUserId).map {
                          case Right(Some(details)) =>
                            Some(
                              ClientDetails(
                                user_id = details.userId,
                                name = s"${details.firstName} ${details.lastName}",
                                email = details.email
                              )
                            )
                          case _ => None
                        }
                      case _ => IO.pure(None)
                    }
                  } yield {
                    clientDetails match {
                      case Some(details) if !acc.exists(_.user_id == clientUserId) =>
                        acc :+ details
                      case _ => acc
                    }
                  }
                }
              case Left(error) =>
                IO.raiseError(new RuntimeException(s"Error fetching VM clients: ${error.getMessage}"))
            }
          } yield activeClients

          onComplete(result.attempt.unsafeToFuture()) {
            case scala.util.Success(clientDetails) =>
              complete((200, Map("client_details" -> ClientDetails).asJson))
            case scala.util.Failure(exception) =>
              complete((500, Map("error" -> exception.getMessage).asJson))
          }
        }
      }
    }
  }
}
