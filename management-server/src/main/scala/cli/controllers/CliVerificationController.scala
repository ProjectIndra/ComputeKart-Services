package cli.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import utils.ErrorResponse
import doobie.implicits._
import java.util.UUID

import main.SqlDB

case class CliVerificationResponse(cli_verification_token: String)
case class CliSessionRequest(cli_verification_token: String, wireguard_endpoint: String, wireguard_public_key: String)
case class CliSessionResponse(message: String, session_token: String)

object CliVerificationController {

  val uiRoutes: Route =
    concat(
      path("getCliVerificationToken") {
        get {
          parameter("user_id") { userId =>
            val cliVerificationToken = UUID.randomUUID().toString

            val updateQuery =
              sql"""
                UPDATE users
                SET cli_verification_token = $cliVerificationToken
                WHERE user_id = $userId
              """.update.run

            val result = SqlDB.transactor.use(xa => updateQuery.transact(xa)).unsafeToFuture()

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
    )

  val cliRoutes: Route =
    concat(
      path("verifyCliToken") {
        post {
          entity(as[CliSessionRequest]) { request =>
            val query =
              sql"""
                SELECT user_id
                FROM users
                WHERE cli_verification_token = ${request.cli_verification_token}
              """.query[String].option

            val result = SqlDB.transactor.use(xa => query.transact(xa)).unsafeToFuture()

            onComplete(result) {
              case scala.util.Success(Some(userId)) =>
                val cliId = UUID.randomUUID().toString
                val sessionToken = UUID.randomUUID().toString
                val sessionExpiryTime = java.time.Instant.now().plusSeconds(365 * 24 * 60 * 60).toString // 1 year

                val insertQuery =
                  sql"""
                    INSERT INTO cli_sessions (user_id, cli_id, cli_wireguard_endpoint, cli_wireguard_public_key, cli_status, cli_session_token, cli_session_token_expiry_timestamp, cli_verification_token)
                    VALUES ($userId, $cliId, ${request.wireguard_endpoint}, ${request.wireguard_public_key}, true, $sessionToken, $sessionExpiryTime, ${request.cli_verification_token})
                  """.update.run

                val insertResult = SqlDB.transactor.use(xa => insertQuery.transact(xa)).unsafeToFuture()

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
    )
}
