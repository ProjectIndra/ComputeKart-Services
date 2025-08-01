package cli.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import middleware.BaseController
import middleware.AuthMiddleware.uiLoginRequired
import utils.ErrorResponse
import cli.CliDetailsRepository

case class DeleteCliSessionResponse(message: String)

object CliSessionController extends BaseController {

  private val securedRoutes: Map[String, String] => Route = request =>
    concat(
      path("getAllCliSessionDetails") {
        get {
          request.get("user_id") match {
            case Some(userId) =>
              val result = CliDetailsRepository.getAllCliSessionDetails(userId).unsafeToFuture()

              onComplete(result) {
                case scala.util.Success(cliSessions) if cliSessions.nonEmpty =>
                  complete((200, Map("cli_session_details" -> cliSessions).asJson))
                case scala.util.Success(_) =>
                  complete((404, ErrorResponse("No active session found").asJson))
                case scala.util.Failure(ex) =>
                  complete((500, ErrorResponse("Database error: " + ex.getMessage).asJson))
              }
            case None =>
              complete((400, ErrorResponse("User ID missing from token").asJson))
          }
        }
      },
      path("deleteCliSession") {
        delete {
          request.get("user_id") match {
            case Some(userId) =>
              request.get("cli_id") match {
                case Some(cliId) =>
                  val result = CliDetailsRepository.deleteCliSession(userId, cliId).unsafeToFuture()

                  onComplete(result) {
                    case scala.util.Success(updatedRows) if updatedRows > 0 =>
                      complete((200, DeleteCliSessionResponse("CLI session deleted successfully").asJson))
                    case scala.util.Success(_) =>
                      complete((404, ErrorResponse("CLI session not found").asJson))
                    case scala.util.Failure(ex) =>
                      complete((500, ErrorResponse("Database error: " + ex.getMessage).asJson))
                  }
                case None =>
                  complete((400, ErrorResponse("Missing cli_id").asJson))
              }
            case None =>
              complete((400, ErrorResponse("User ID missing from token").asJson))
          }
        }
      }
    )

  val routes: Route = uiLoginRequired { request =>
    securedRoutes(request)
  }
}
