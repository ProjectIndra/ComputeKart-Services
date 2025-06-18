package users.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import doobie.implicits._

import main.DB
import utils.{CryptoUtils, ErrorResponse}

case class LoginRequest(username_or_email: String, password: String)
case class LoginResponse(message: String, token: String)

object LoginController {
  def login: Route = path("login") {
    post {
      entity(as[LoginRequest]) { request =>
        if (request.username_or_email.isEmpty || request.password.isEmpty) {
          complete((400, ErrorResponse("Username/email and password are required").asJson))
        } else {
          val query =
            sql"""
              SELECT user_id, username, email, password
              FROM users
              WHERE username = ${request.username_or_email} OR email = ${request.username_or_email}
            """.query[(String, String, String, String)].option

          val result = DB.transactor.use(xa => query.transact(xa)).unsafeToFuture()

          onComplete(result) {
            case scala.util.Success(Some((userId, username, email, hashedPassword))) =>
              if (CryptoUtils.verifyPassword(request.password, hashedPassword)) {
                val token = CryptoUtils.encodeJwt(userId, username, email)
                complete((200, LoginResponse("Login successful", token).asJson))
              } else {
                complete((401, ErrorResponse("Invalid username/email or password").asJson))
              }

            case scala.util.Success(None) =>
              complete((401, ErrorResponse("Invalid username/email or password").asJson))

            case scala.util.Failure(ex) =>
              complete((500, ErrorResponse("Database error: " + ex.getMessage).asJson))
          }
        }
      }
    }
  }
}