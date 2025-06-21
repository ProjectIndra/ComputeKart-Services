package cli.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import utils.ErrorResponse
import doobie.implicits._

import main.SqlDB

case class CliSessionDetails(cli_id: String, cli_wireguard_endpoint: String, cli_wireguard_public_key: String, cli_status: Boolean)
case class DeleteCliSessionResponse(message: String)

object CliSessionController {

  // Routes for UI-related paths
  val routes: Route =
    concat(
      path("getAllCliSessionDetails") {
        get {
          parameter("user_id") { userId =>
            val query =
              sql"""
                SELECT cli_id, cli_wireguard_endpoint, cli_wireguard_public_key, cli_status
                FROM cli_sessions
                WHERE user_id = $userId AND cli_status = true
              """.query[CliSessionDetails].to[List]

            val result = SqlDB.transactor.use(xa => query.transact(xa)).unsafeToFuture()

            onComplete(result) {
              case scala.util.Success(cliSessions) if cliSessions.nonEmpty =>
                complete((200, Map("cli_session_details" -> cliSessions).asJson))
              case scala.util.Success(_) =>
                complete((404, ErrorResponse("No active session found").asJson))
              case scala.util.Failure(ex) =>
                complete((500, ErrorResponse("Database error: " + ex.getMessage).asJson))
            }
          }
        }
      },
      path("deleteCliSession") {
        get {
          parameters("user_id", "cli_id") { (userId, cliId) =>
            val updateQuery =
              sql"""
                UPDATE cli_sessions
                SET cli_status = false
                WHERE user_id = $userId AND cli_id = $cliId
              """.update.run

            val result = SqlDB.transactor.use(xa => updateQuery.transact(xa)).unsafeToFuture()

            onComplete(result) {
              case scala.util.Success(updatedRows) if updatedRows > 0 =>
                complete((200, DeleteCliSessionResponse("CLI session deleted successfully").asJson))
              case scala.util.Success(_) =>
                complete((404, ErrorResponse("CLI session not found").asJson))
              case scala.util.Failure(ex) =>
                complete((500, ErrorResponse("Database error: " + ex.getMessage).asJson))
            }
          }
        }
      }
    )
}
