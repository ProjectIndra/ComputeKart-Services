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
        temp_token VARCHAR(255) PRIMARY KEY,
        user_id VARCHAR(255) NOT NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        is_used BOOLEAN DEFAULT FALSE,
        service_id VARCHAR(255) NULL,
        attributes JSON NOT NULL
        )
      """.update.run

    // Use the transactor properly
    SqlDB.transactor.use { xa =>
      createTableQuery.transact(xa)
    }
  }
}
