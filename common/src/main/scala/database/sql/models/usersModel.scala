package users.models

import cats.effect.IO
import doobie._
import doobie.implicits._

import main.SqlDB

object UserModel {
  def createUsersTable(): IO[Int] = {
    val createTableQuery =
      sql"""
        CREATE TABLE IF NOT EXISTS users (
          user_id VARCHAR(255) PRIMARY KEY,
          username VARCHAR(255) NOT NULL,
          email VARCHAR(255) NOT NULL UNIQUE,
          password VARCHAR(255) NOT NULL,
          cli_verification_token VARCHAR(255),
          profile_name VARCHAR(255),
          profile_image VARCHAR(255)
        )
      """.update.run

    // Use the transactor properly
    SqlDB.transactor.use { xa =>
      createTableQuery.transact(xa)
    }
  }
}
