package users.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.circe.generic.auto._
import io.circe.syntax._
import java.util.UUID
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import doobie.implicits._
import java.util.UUID

import main.SqlDB
import utils.{CryptoUtils, ErrorResponse}

case class RegisterRequest(username: String, email: String, password: String)
case class RegisterResponse(message: String)

object RegisterController {
  def register: Route = path("register") {
    post {
      entity(as[RegisterRequest]) { request =>
        println("Register endpoint hit with request: " + request)

        // Validate input
        if (!request.email.contains("@")) {
          complete((400, ErrorResponse("Invalid email").asJson))
        } else if (request.password.length < 6) {
          complete((400, ErrorResponse("Password must be at least 6 characters long").asJson))
        } else if (request.username.length < 3) {
          complete((400, ErrorResponse("Username must be at least 3 characters long").asJson))
        } else {
          val userId = UUID.randomUUID().toString
          val encryptedToken = CryptoUtils.encrypt(userId)
          val hashedPassword = CryptoUtils.hashPassword(request.password)

          val insertQuery =
            sql"""
              INSERT INTO users (user_id, username, email, password, cli_verification_token)
              VALUES ($userId, ${request.username}, ${request.email}, $hashedPassword, $encryptedToken)
            """.update.run

          val result = SqlDB.runUpdateQuery(insertQuery, "Error inserting user data").unsafeToFuture()

          onComplete(result) {
            case scala.util.Success(_) =>
              complete((201, RegisterResponse("User registered successfully").asJson))

            case scala.util.Failure(ex) if ex.getMessage.contains("Duplicate entry") =>
              if (ex.getMessage.contains("username")) {
                complete((400, ErrorResponse("Username already exists").asJson))
              } else if (ex.getMessage.contains("email")) {
                complete((400, ErrorResponse("Email already exists").asJson))
              } else {
                complete((500, ErrorResponse("Database error: " + ex.getMessage).asJson))
              }

            case scala.util.Failure(ex) =>
              complete((500, ErrorResponse("Database error: " + ex.getMessage).asJson))
          }
        }
      }
    }
  }
}
