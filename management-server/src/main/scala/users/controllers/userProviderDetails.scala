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
        case (key, Left(error))  => key -> Json.obj("error" -> error.getMessage.asJson)
      }.toSeq: _*
    )
  }

  def getUserProviderDetails: Route = path("userProviderDetails") {
    get {
      uiLoginRequired { request =>
        request.get("user_id") match {
          case Some(userId) =>
            println(s"Fetching provider details for user: $userId")

            val result = for {
              providersEither <- ProviderDetailsRepository.getProvidersByUserId(userId)
              providers <- IO.fromEither(providersEither).adaptError {
                case ex => new RuntimeException(s"Error fetching providers: ${ex.getMessage}")
              }
              providersWithConf <- IO.traverse(providers) { provider =>
                ProviderDetailsRepository.getProviderConf(provider.providerId).map {
                  case Right(conf) =>
                    provider.asJson.deepMerge(conf.asJson).asObject.map(_.toMap).getOrElse(Map.empty)
                  case Left(_) =>
                    provider.asJson.asObject.map(_.toMap).getOrElse(Map.empty)
                }
              }
            } yield providersWithConf

            onComplete(result.attempt.unsafeToFuture()) {
              case scala.util.Success(Right(providers)) =>
                println(s"Successfully fetched provider details for user: $userId")
                complete((200, Map("all_providers" -> providers).asJson))
              case scala.util.Success(Left(error)) =>
                println(s"Error fetching provider details: ${error.getMessage}")
                complete((500, Map("error" -> error.getMessage).asJson))
              case scala.util.Failure(exception) =>
                println(s"Unexpected error fetching provider details: ${exception.getMessage}")
                complete((500, Map("error" -> exception.getMessage).asJson))
            }

          case None =>
            println("User ID missing from request")
            complete((400, Map("error" -> "User ID missing from request").asJson))
        }
      }
    }
  }
}
