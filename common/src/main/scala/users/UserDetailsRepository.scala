package users

import cats.effect.IO
import doobie.implicits._
import main.SqlDB

case class UserDetails(
  userId: String,
  email: String,
  username: String
)

object UserDetailsRepository {

  def getClientDetails(clientUserId: String): IO[Either[Throwable, Option[UserDetails]]] = {
    val query =
      sql"""
        SELECT user_id, email, username
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

  def updateUserDetails(
    userId: String,
    profileName: Option[String],
    profileImage: Option[String]
  ): IO[Int] = {
    val query =
      sql"""
        UPDATE users
        SET profile_name = $profileName, profile_image = $profileImage
        WHERE user_id = $userId
      """.update.run

    SqlDB.transactor.use { xa =>
      query.transact(xa)
    }
  }
}
