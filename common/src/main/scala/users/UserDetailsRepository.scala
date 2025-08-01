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

  /** Retrieves client details by user ID. */
  def getClientDetails(clientUserId: String): IO[Either[Throwable, UserDetails]] = {
    val query =
      sql"""
        SELECT user_id, email, username
        FROM users
        WHERE user_id = $clientUserId
      """.query[UserDetails].option

    SqlDB.runQueryEither(query, "Get Client Details")
  }

  /** Updates user details (profile name and profile image). */
  def updateUserDetails(
    userId: String,
    profileName: Option[String],
    profileImage: Option[String]
  ): IO[Either[Throwable, Unit]] = {
    val query =
      sql"""
        UPDATE users
        SET profile_name = $profileName, profile_image = $profileImage
        WHERE user_id = $userId
      """.update.run

    SqlDB.runUpdateQuery(query, "Update User Details")
  }
}
