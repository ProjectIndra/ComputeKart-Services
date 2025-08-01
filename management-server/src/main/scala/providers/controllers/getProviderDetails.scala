package providers.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import providers.ProviderDetails
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import providers.ProviderDetailsRepository

import middleware.BaseController

object ProviderDetilsController extends BaseController {

  // Implicit Encoders
  implicit def eitherEncoder[A, B](implicit encodeA: Encoder[A], encodeB: Encoder[B]): Encoder[Either[A, B]] = Encoder.instance {
    case Left(a)  => Json.obj("Left" -> encodeA(a))
    case Right(b) => Json.obj("Right" -> encodeB(b))
  }

  // Implicit Encoder for Map[String, Either[Throwable, ProviderDetails]]
  implicit val eitherThrowableProviderDetailsEncoder: Encoder[Either[Throwable, ProviderDetails]] = Encoder.instance {
    case Left(error)  => Json.obj("error" -> Json.fromString(error.getMessage))
    case Right(value) => Json.obj("data" -> value.asJson)
  }

  implicit val mapEncoder: Encoder[Map[String, Either[Throwable, ProviderDetails]]] = Encoder.encodeMap[String, Either[Throwable, ProviderDetails]]

  // Implicit Encoder for Map[String, Either[Throwable, List[ProviderDetails]]]
  implicit val eitherThrowableListProviderDetailsEncoder: Encoder[Either[Throwable, List[ProviderDetails]]] = Encoder.instance {
    case Left(error)  => Json.obj("error" -> Json.fromString(error.getMessage))
    case Right(value) => Json.obj("data" -> value.asJson)
  }

  implicit val mapListEncoder: Encoder[Map[String, Either[Throwable, List[ProviderDetails]]]] = Encoder.encodeMap[String, Either[Throwable, List[ProviderDetails]]]

  // Route for fetching provider lists
  def providersLists: Route = path("lists") {
    get {
      uiLoginRequired { user =>
        val userId = user.get("user_id")

        if (userId.isEmpty) {
          complete((400, Map("error" -> "User ID is required").asJson))
        } else {
          parameter("provider_name".?) { providerNameOpt =>
            val result = providerNameOpt match {
              case Some(providerName) if providerName.nonEmpty =>
                ProviderDetailsRepository.getProvidersListFiltered(userId.get, providerName)
              case _ =>
                ProviderDetailsRepository.getProvidersList(userId.get)
            }

            onComplete(result.attempt.unsafeToFuture()) {
              case scala.util.Success(Right(response)) =>
                complete((200, Map("all_providers" -> response).asJson))
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

  // Route for fetching provider details
  def providersDetails: Route = path("details") {
    post {
      uiLoginRequired { user =>
        entity(as[Map[String, String]]) { request =>
          val providerId = request.get("provider_id")

          if (providerId.isEmpty) {
            complete((400, Map("error" -> "Provider ID is required").asJson))
          } else {
            val result = ProviderDetailsRepository.fetchProviderDetails(providerId.get)

            onComplete(result.attempt.unsafeToFuture()) {
              case scala.util.Success(Right(response)) =>
                complete((200, Map("data" -> response).asJson)) // Wrap response in "data" key
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
