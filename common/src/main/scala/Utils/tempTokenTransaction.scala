package utils

import cats.effect.IO
import doobie.implicits._
import doobie.util.{Get, Put}
import io.circe.parser.decode
import io.circe.syntax._
import io.circe.Json
import main.SqlDB
import java.time.LocalDateTime

object TempTokenTransaction {

  // Provide Get and Put instances for io.circe.Json
  implicit val jsonGet: Get[Json] = Get[String].temap(decode[Json](_).left.map(_.getMessage))
  implicit val jsonPut: Put[Json] = Put[String].contramap(_.noSpaces)

  /** Creates a temporary token for a specific service and user. */
  def createTempToken(tempToken: String, userId: String, attributes: Json, serviceId: Option[String] = None): IO[Either[Throwable, Unit]] = {
    val createdAt = LocalDateTime.now().toString.replace("T", " ").split("\\.")(0) // Format to "YYYY-MM-DD HH:MM:SS"
    val isUsed = false
    val attributesStr = attributes.noSpaces

    val query =
      sql"""
        INSERT INTO temp_token (temp_token, user_id, service_id, attributes, created_at, is_used)
        VALUES ($tempToken, $userId, $serviceId, $attributesStr, $createdAt, $isUsed)
      """.update.run

    SqlDB.runUpdateQuery(query, "Create Temp Token")
  }

  /** Deletes a temporary token. */
  def removeTempToken(tempToken: String): IO[Either[Throwable, Unit]] = {
    val query =
      sql"""
        DELETE FROM temp_token
        WHERE temp_token = $tempToken
      """.update.run

    SqlDB.runUpdateQuery(query, "Remove Temp Token")
  }

  /** Verifies the temporary token and marks it as used in a single transaction. */
  def verifyTempTokenAndGetDetails(tempToken: String): IO[Either[Throwable, (String, Option[String])]] = {
    if (tempToken.isEmpty) {
      IO.raiseError(new Exception("Temp token cannot be empty"))
    } else {
      val selectQuery =
        sql"""
          SELECT user_id, service_id, attributes
          FROM temp_token
          WHERE temp_token = $tempToken
            AND is_used = false
            AND created_at >= NOW() - INTERVAL '1 hour'
        """.query[(String, Option[String], Json)].option

      val updateQuery =
        sql"""
          UPDATE temp_token
          SET is_used = true
          WHERE temp_token = $tempToken
        """.update.run

      (for {
        maybeResult <- selectQuery.transact(SqlDB.transactor)
        _ <- maybeResult match {
          case Some(_) => updateQuery.transact(SqlDB.transactor).void
          case None    => IO.unit
        }
      } yield maybeResult.map { case (userId, serviceIdOpt, _) => (userId, serviceIdOpt) })
        .attempt
        .map {
          case Right(Some(result)) => Right(result)
          case Right(None)         => Left(new Exception("Temp token not found or expired"))
          case Left(error)         => Left(error)
        }
    }
  }
}
