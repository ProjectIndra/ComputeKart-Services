package middleware

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive1, Route}
import akka.http.scaladsl.model.{StatusCodes, HttpResponse, HttpEntity, ContentTypes}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.Json
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import java.time.LocalDateTime
import scala.util.{Failure, Success}
import com.typesafe.config.ConfigFactory
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import pdi.jwt.exceptions.JwtException

import cli.CliDetailsRepository
import users.UserDetailsRepository

case class User(userId: String, username: String, cliId: Option[String])

object AuthMiddleware {

  private val config = ConfigFactory.load()
  private val SECRET_KEY: String = config.getString("crypto.secretKey")

 def uiLoginRequired(route: Map[String, String] => Route): Route = {
  extractRequestContext { ctx =>
    val authorizationHeader = ctx.request.headers.find(_.name == "Authorization").map(_.value)
    authorizationHeader match {
      case Some(authHeader) =>
        val tokenType = authHeader.split(" ").headOption.getOrElse("")
        val token = authHeader.split(" ").lift(1).getOrElse("")

        tokenType match {
          case "BearerCLI" =>
            onComplete(verifyCliToken(token).unsafeToFuture()) {
              case scala.util.Success(Right(user)) =>
                val updatedRequest = ctx.request.entity match {
                  case HttpEntity.Strict(contentType, data) =>
                    val existingRequest = io.circe.parser.parse(data.utf8String).getOrElse(Json.obj()).as[Map[String, String]].getOrElse(Map.empty)
                    existingRequest + ("user_id" -> user.userId)
                  case _ => Map("user_id" -> user.userId)
                }
                route(updatedRequest)
              case scala.util.Success(Left(error)) =>
                complete(HttpResponse(StatusCodes.Unauthorized, entity = HttpEntity(ContentTypes.`application/json`, Map("error" -> error).asJson.noSpaces)))
              case scala.util.Failure(exception) =>
                complete(HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(ContentTypes.`application/json`, Map("error" -> exception.getMessage).asJson.noSpaces)))
            }

          case "Bearer" =>
            onComplete(verifyUiToken(token).unsafeToFuture()) {
              case scala.util.Success(Right(user)) =>
                val updatedRequest = ctx.request.entity match {
                  case HttpEntity.Strict(contentType, data) =>
                    val existingRequest = io.circe.parser.parse(data.utf8String).getOrElse(Json.obj()).as[Map[String, String]].getOrElse(Map.empty)
                    existingRequest + ("user_id" -> user.userId)
                  case _ => Map("user_id" -> user.userId)
                }
                route(updatedRequest)
              case scala.util.Success(Left(error)) =>
                complete(HttpResponse(StatusCodes.Unauthorized, entity = HttpEntity(ContentTypes.`application/json`, Map("error" -> error).asJson.noSpaces)))
              case scala.util.Failure(exception) =>
                complete(HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(ContentTypes.`application/json`, Map("error" -> exception.getMessage).asJson.noSpaces)))
            }

          case _ =>
            complete(HttpResponse(StatusCodes.Unauthorized, entity = HttpEntity(ContentTypes.`application/json`, Map("error" -> "Invalid token format").asJson.noSpaces)))
        }

      case None =>
        complete(HttpResponse(StatusCodes.Unauthorized, entity = HttpEntity(ContentTypes.`application/json`, Map("error" -> "Authorization header missing").asJson.noSpaces)))
    }
  }
}

  private def verifyCliToken(token: String): IO[Either[String, User]] = {
    for {
      cliDetailsOpt <- CliDetailsRepository.findCliSessionByToken(token)
      cliDetails <- IO.fromOption(cliDetailsOpt)(new Exception("Invalid token"))
      _ <- IO.raiseUnless(LocalDateTime.parse(cliDetails.cli_session_token_expiry_timestamp).isAfter(LocalDateTime.now()))(new Exception("Token expired"))
      userEither <- UserDetailsRepository.getClientDetails(cliDetails.user_id)
      user <- IO.fromEither(userEither).flatMap {
        case Some(userDetails) => IO.pure(userDetails)
        case None => IO.raiseError(new Exception("Invalid user"))
      }
    } yield Right(User(user.userId, user.username, Some(cliDetails.cli_id)))
  }

  private def verifyUiToken(token: String): IO[Either[String, User]] = {
    for {
      decoded <- IO(Jwt.decode(token, SECRET_KEY, Seq(JwtAlgorithm.HS256)).toEither)
        .handleErrorWith {
          case ex: JwtException if ex.getMessage.contains("expired") => IO.pure(Left("Token expired"))
          case _: JwtException => IO.pure(Left("Invalid token"))
          case ex: Exception => IO.pure(Left(s"Unexpected error: ${ex.getMessage}"))
        }

      userId <- IO.fromEither(
        decoded
          .flatMap { claim =>
            io.circe.parser
              .parse(claim.content)
              .flatMap(_.hcursor.get[String]("user_id"))
              .left
              .map(error => s"Failed to extract user_id: ${error.getMessage}")
          }
          .left
          .map(error => new Exception(s"Failed to parse token: ${error}"))
      )

      userDetails <- UserDetailsRepository.getClientDetails(userId).flatMap {
        case Right(Some(user)) => IO.pure(Right(User(user.userId, user.username, None)))
        case Right(None) => IO.pure(Left("Invalid user"))
        case Left(error) => IO.pure(Left(s"Error fetching user details: ${error.getMessage}"))
      }
    } yield userDetails
  }

}
