package cli.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import utils.ErrorResponse
import cli.CliDetailsRepository

case class DeleteCliSessionResponse(message: String)

object CliSessionController {

  // Routes for UI-related paths
  val routes: Route =
    concat(
      path("getAllCliSessionDetails") {
        get {
          parameter("user_id") { userId =>
            val result = CliDetailsRepository.getAllCliSessionDetails(userId).unsafeToFuture()

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
            val result = CliDetailsRepository.deleteCliSession(userId, cliId).unsafeToFuture()

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
