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

  def uiLoginRequired(route: Map[String, String] => Route): Route = extractRequestContext { ctx =>
    val authHeaderOpt = ctx.request.headers.find(_.is("authorization")).map(_.value)

    authHeaderOpt match {
      case Some(authHeader) =>
        val parts = authHeader.split(" ")
        if (parts.length != 2) {
          complete(StatusCodes.Unauthorized, "Invalid Authorization header format")
        } else {
          val tokenType = parts(0)
          val token = parts(1)

          val resultIO: IO[Either[String, User]] = tokenType match {
            case "BearerCLI" => verifyCliToken(token)
            case "Bearer" => verifyUiToken(token)
            case _ => IO.pure(Left("Invalid token type"))
          }

          onComplete(resultIO.unsafeToFuture()) {
            case Success(Right(user)) =>
              val method = ctx.request.method.value
              method match {
                case "POST" | "PUT" =>
                  entity(as[String]) { body =>
                    val parsedJson = io.circe.parser.parse(body).getOrElse(Json.obj())
                    val requestMap = parsedJson.as[Map[String, String]].getOrElse(Map.empty)
                    val finalMap = requestMap + ("user_id" -> user.userId)
                    route(finalMap)
                  }

                case "GET" | "DELETE" =>
                  parameterMap { params =>
                    val finalMap = params + ("user_id" -> user.userId)
                    route(finalMap)
                  }

                case _ =>
                  complete(StatusCodes.MethodNotAllowed)
              }

            case Success(Left(error)) =>
              complete(StatusCodes.Unauthorized, s"""{"error":"$error"}""")

            case Failure(ex) =>
              complete(StatusCodes.InternalServerError, s"""{"error":"${ex.getMessage}"}""")
          }
        }

      case None =>
        complete(StatusCodes.Unauthorized, "Authorization header missing")
    }
  }

  private def verifyCliToken(token: String): IO[Either[String, User]] = {
    for {
      cliDetailsOpt <- CliDetailsRepository.findCliSessionByToken(token)
      cliDetails <- IO.fromOption(cliDetailsOpt)(new Exception("Invalid token"))
      _ <- IO.raiseUnless(
        LocalDateTime.parse(cliDetails.cli_session_token_expiry_timestamp).isAfter(LocalDateTime.now())
      )(new Exception("Token expired"))
      userEither <- UserDetailsRepository.getClientDetails(cliDetails.user_id)
      user <- IO.fromEither(userEither).adaptError { case ex =>
        new Exception(s"Error fetching user details: ${ex.getMessage}")
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
        case Right(user: users.UserDetails) => IO.pure(Right(User(user.userId, user.username, None)))
        case Left(error) => IO.pure(Left(s"Error fetching user details: ${error.getMessage}"))
      }
    } yield userDetails
  }

}
