package users.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import middleware.BaseController

import utils.ErrorResponse
import users.UserDetailsRepository

case class UserResponse(user_id: String, username: String, email: String, profile_name: Option[String], profile_image: Option[String])
case class UpdateUserRequest(profile_name: Option[String], profile_image: Option[String])
case class UpdateUserResponse(message: String)

object UserInfoController extends BaseController {

  val routes: Route =
    concat(
      path("getUserDetails") {
        get {
          parameter("user_id") { userId =>
            val result = UserDetailsRepository.getClientDetails(userId).unsafeToFuture()

            onComplete(result) {
              case scala.util.Success(Right(Some(user))) =>
                complete((200, user.asJson))
              case scala.util.Success(Right(None)) =>
                complete((404, ErrorResponse("User not found").asJson))
              case scala.util.Success(Left(error)) =>
                complete((500, ErrorResponse("Database error: " + error.getMessage).asJson))
              case scala.util.Failure(ex) =>
                complete((500, ErrorResponse("Unexpected error: " + ex.getMessage).asJson))
            }
          }
        }
      },
      path("updateUserDetails") {
        put {
          entity(as[UpdateUserRequest]) { request =>
            parameter("user_id") { userId =>
              val validatedProfileImage = request.profile_image match {
                case Some(image) if image.nonEmpty =>
                  Some(image)
                case _ =>
                  None
              }

              val validatedProfileName = request.profile_name match {
                case Some(name) if name.nonEmpty =>
                  Some(name)
                case _ =>
                  None
              }

              val result = UserDetailsRepository.updateUserDetails(userId, validatedProfileName, validatedProfileImage).unsafeToFuture()

              onComplete(result) {
                case scala.util.Success(updatedRows) if updatedRows > 0 =>
                  complete((200, UpdateUserResponse("User details updated successfully").asJson))
                case scala.util.Success(_) =>
                  complete((404, ErrorResponse("No changes made or user not found").asJson))
                case scala.util.Failure(ex) =>
                  complete((500, ErrorResponse("Database error: " + ex.getMessage).asJson))
              }
            }
          }
        }
      }
    )
}
