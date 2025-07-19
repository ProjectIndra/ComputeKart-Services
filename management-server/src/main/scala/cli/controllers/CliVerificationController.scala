package cli.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import java.util.UUID

import middleware.BaseController
import utils.ErrorResponse
import cli.CliDetailsRepository

case class CliVerificationResponse(cli_verification_token: String)
case class CliSessionRequest(cli_verification_token: String, wireguard_endpoint: String, wireguard_public_key: String)
case class CliSessionResponse(message: String, session_token: String)

object CliVerificationController extends BaseController {
  
  val uiRoutes: Route =
    concat(
      path("getCliVerificationToken") {
        get {
          uiLoginRequired { user =>
            val userId = user.get("user_id")

            if (userId.isEmpty) {
              complete((400, ErrorResponse("User ID is required").asJson))
            } else {
              val cliVerificationToken = UUID.randomUUID().toString
              val result = CliDetailsRepository.updateCliVerificationToken(userId.get, cliVerificationToken).unsafeToFuture()

              onComplete(result) {
                case scala.util.Success(updatedRows) if updatedRows > 0 =>
                  complete((200, CliVerificationResponse(cliVerificationToken).asJson))
                case scala.util.Success(_) =>
                  complete((404, ErrorResponse("User not found").asJson))
                case scala.util.Failure(ex) =>
                  complete((500, ErrorResponse("Database error: " + ex.getMessage).asJson))
              }
            }
          }
        }
      }
    )

  val cliRoutes: Route =
    concat(
      path("verifyCliToken") {
        post {
          uiLoginRequired { user =>
            entity(as[CliSessionRequest]) { request =>
              val result = CliDetailsRepository.verifyCliToken(request.cli_verification_token).unsafeToFuture()

              onComplete(result) {
                case scala.util.Success(Some(userId)) =>
                  val cliId = UUID.randomUUID().toString
                  val sessionToken = UUID.randomUUID().toString
                  val sessionExpiryTime = java.time.Instant.now().plusSeconds(365 * 24 * 60 * 60).toString // 1 year

                  val insertResult = CliDetailsRepository.insertCliSession(
                    userId,
                    cliId,
                    request.wireguard_endpoint,
                    request.wireguard_public_key,
                    sessionToken,
                    sessionExpiryTime,
                    request.cli_verification_token
                  ).unsafeToFuture()

                  onComplete(insertResult) {
                    case scala.util.Success(_) =>
                      complete((200, CliSessionResponse("Token verified successfully", sessionToken).asJson))
                    case scala.util.Failure(ex) =>
                      complete((500, ErrorResponse("Database error: " + ex.getMessage).asJson))
                  }

                case scala.util.Success(None) =>
                  complete((401, ErrorResponse("Invalid token").asJson))
                case scala.util.Failure(ex) =>
                  complete((500, ErrorResponse("Database error: " + ex.getMessage).asJson))
              }
            }
          }
        }
      }
    )
}
