package utils

import cats.effect.IO
import doobie.implicits._
import doobie.implicits._
import io.circe.parser.decode
import io.circe.syntax._
import doobie.util.Get
import doobie.util.Put

import doobie.util.transactor.Transactor
import main.SqlDB
import io.circe.Json
import java.util.UUID

import utils.models.TempTokenModel

/** This object handles the transaction for temporary token creation and management. It provides methods to create, remove, and verify temporary tokens associated with a user and a service. It's used to verify the provider and CLI tokens for user sessions. tempToken is a temporary table that stores tokens for various services like provider and CLI. And then after the verification of the token, it can be used to grant access to the requested resources.
  */
object TempTokenTransaction {

  // Provide Get and Put instances for io.circe.Json
  implicit val jsonGet: Get[Json] = Get[String].temap(str => decode[Json](str).left.map(_.getMessage))
  implicit val jsonPut: Put[Json] = Put[String].contramap(_.noSpaces)

  /** Creates a temporary token for a specific service and user.
    *
    * @param serviceId
    *   The ID of the service associated with the token.
    * @param userId
    *   The ID of the user associated with the token.
    * @param attributes
    *   Additional attributes to be stored with the token in JSON format.
    * @return
    *   The number of rows affected by the insert operation.
    */
  def createTempToken(tempToken: String, userId: String, attributes: Json, serviceId: Option[String] = None): IO[Int] = {
    val createdAt = java.time.LocalDateTime.now().toString.replace("T", " ").split("\\.")(0) // Format to "YYYY-MM-DD HH:MM:SS"
    val isUsed = false
    val attributesStr = attributes.noSpaces
    val query =
      sql"""
            INSERT INTO temp_token (temp_token, user_id, service_id, attributes, created_at, is_used)
            VALUES ($tempToken, $userId, $serviceId, $attributesStr, $createdAt, $isUsed)
        """.update.run

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(rowsAffected) => rowsAffected
        case Left(error) => throw new Exception(s"Error creating temp token: ${error.getMessage}")
      }
    }
  }

  def removeTempToken(tempToken: String): IO[Int] = {
    val query =
      sql"""
            DELETE FROM temp_token
            WHERE temp_token = $tempToken
        """.update.run
    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(rowsAffected) => rowsAffected
        case Left(error) => throw new Exception(s"Error removing temp token: ${error.getMessage}")
      }
    }
  }

  /** Marks a temporary token as used.
    *
    * @param tempToken
    *   The temporary token to mark as used.
    * @return
    *   The number of rows affected by the update operation.
    */
  def markTempTokenAsUsed(tempToken: String): IO[Int] = {
    val query =
      sql"""
            UPDATE temp_token
            SET is_used = true
            WHERE temp_token = $tempToken
        """.update.run
    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(rowsAffected) => rowsAffected
        case Left(error) => throw new Exception(s"Error marking temp token as used: ${error.getMessage}")
      }
    }
  }

  /** Verifies the temporary token and retrieves the associated user ID and service ID. If the token is valid, it marks the token as used.
    *
    * @param tempToken
    *   The temporary token to verify.
    * @return
    *   An optional tuple containing user ID and service ID if the token is valid, otherwise None.
    */
  def verifyTempTokenAndGetDetails(tempToken: String): IO[Option[(String, Option[String])]] = {
    val query =
      sql"""
      SELECT user_id, service_id, attributes
      FROM temp_token
      WHERE temp_token = $tempToken AND is_used = false AND created_at >= NOW() - INTERVAL '1 hour'
    """.query[(String, Option[String], Json)].option

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.flatMap {
        case Right(Some((userId, serviceIdOpt, attributes))) =>
          // Mark the token as used after successful verification
          markTempTokenAsUsed(tempToken).map(_ => Some((userId, serviceIdOpt)))

        case Right(None) =>
          // Token not found or already used
          IO.pure(None)

        case Left(error) =>
          // Handle database or query errors
          IO.raiseError(new Exception(s"Error verifying temp token: ${error.getMessage}"))
      }
    }
  }
}
