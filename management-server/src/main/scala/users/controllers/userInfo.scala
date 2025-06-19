package users.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import utils.{CryptoUtils, ErrorResponse}
import doobie.implicits._

import main.SqlDB

case class UserResponse(user_id: String, username: String, email: String, profile_name: Option[String], profile_image: Option[String])
case class UpdateUserRequest(profile_name: Option[String], profile_image: Option[String])
case class UpdateUserResponse(message: String)

object UserInfoController {

  val routes: Route =
    concat(
      path("getUserDetails") {
        get {
          parameter("user_id") { userId =>
            val query =
              sql"""
                SELECT user_id, username, email, profile_name, profile_image
                FROM users
                WHERE user_id = $userId
              """.query[UserResponse].option

            val result = SqlDB.transactor.use(xa => query.transact(xa)).unsafeToFuture()

            onComplete(result) {
              case scala.util.Success(Some(user)) =>
                complete((200, user.asJson))
              case scala.util.Success(None) =>
                complete((404, ErrorResponse("User not found").asJson))
              case scala.util.Failure(ex) =>
                complete((500, ErrorResponse("Database error: " + ex.getMessage).asJson))
            }
          }
        }
      },
      path("updateUserDetails") {
        put {
          entity(as[UpdateUserRequest]) { request =>
            parameter("user_id") { userId =>
              val updateQuery =
                sql"""
                  UPDATE users
                  SET profile_name = ${request.profile_name}, profile_image = ${request.profile_image}
                  WHERE user_id = $userId
                """.update.run

              val result = SqlDB.transactor.use(xa => updateQuery.transact(xa)).unsafeToFuture()

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