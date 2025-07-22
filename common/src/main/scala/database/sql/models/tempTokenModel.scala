package utils.models

import cats.effect.IO
import doobie._
import doobie.implicits._

import main.SqlDB

object TempTokenModel {
  def createTempTokenModel(): IO[Int] = {
    val createTableQuery =
      sql"""
        CREATE TABLE IF NOT EXISTS temp_token (
            temp_token VARCHAR(255) PRIMARY KEY, -- Unique identifier for the token
            user_id VARCHAR(255) NOT NULL, -- ID of the user associated with the token
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Timestamp when the token was created
            is_used BOOLEAN DEFAULT FALSE -- Indicates if the token has been used
            service_id VARCHAR(255), -- ID of the service like provider & cli associated with the token
            attributes JSON NOT NULL -- JSON data associated with the token
        )
      """.update.run

    // Use the transactor properly
    SqlDB.transactor.use { xa =>
      createTableQuery.transact(xa)
    }
  }
}


