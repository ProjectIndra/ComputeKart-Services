package users.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import providers.ProviderDetailsRepository
import io.circe.{Encoder, Json}

import middleware.BaseController

object UserProviderDetailsController extends BaseController {

  implicit val customEncoder: Encoder[Map[String, Either[Throwable, List[Map[String, Json]]]]] = Encoder.instance { map =>
    Json.obj(
      map.map {
        case (key, Right(value)) => key -> value.asJson
        case (key, Left(error)) => key -> Json.obj("error" -> error.getMessage.asJson)
      }.toSeq: _*
    )
  }

  def getUserProviderDetails: Route = path("userProviderDetails") {
    post {
      entity(as[Map[String, String]]) { request =>
        val userId = request.get("user_id")

        if (userId.isEmpty) {
          complete((400, Map("error" -> "User ID is required").asJson))
        } else {
          val result = for {
            providers <- ProviderDetailsRepository.getProvidersByUserId(userId.get)
            providersWithConf <- IO {
              providers.map { provider =>
                ProviderDetailsRepository.getProviderConf(provider.providerId).unsafeRunSync() match {
                  case Some(conf) =>
                    provider.asJson.deepMerge(conf.asJson).asObject.map(_.toMap).getOrElse(Map.empty)
                  case None =>
                    provider.asJson.asObject.map(_.toMap).getOrElse(Map.empty)
                }
              }
            }
          } yield providersWithConf

          onComplete(result.attempt.unsafeToFuture()) {
            case scala.util.Success(providers) =>
              complete((200, Map("all_providers" -> providers).asJson))
            case scala.util.Failure(exception) =>
              complete((500, Map("error" -> exception.getMessage).asJson))
          }
        }
      }
    }
  }
}
