package users

import cats.effect.IO
import doobie.implicits._
import main.SqlDB

case class UserDetails(
    userId: String,
    email: String,
    firstName: String,
    lastName: String
)

object UserDetailsRepository {

  def getClientDetails(clientUserId: String): IO[Either[Throwable, Option[UserDetails]]] = {
    val query =
      sql"""
        SELECT user_id, email, first_name, last_name
        FROM users
        WHERE user_id = $clientUserId
      """.query[UserDetails].option

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(clientDetails) => Right(clientDetails)
        case Left(e) =>
          println(s"Error fetching client details for user $clientUserId: ${e.getMessage}")
          Left(e)
      }
    }
  }
}