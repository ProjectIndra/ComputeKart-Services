package cli.models

import cats.effect.IO
import doobie._
import doobie.implicits._

import main.SqlDB

object CliModel {
  def createCliSessionsTable(): IO[Int] = {
    val createTableQuery =
      sql"""
        CREATE TABLE IF NOT EXISTS cli_sessions (
          user_id VARCHAR(255) NOT NULL, -- Link session to user
          cli_id VARCHAR(255) NOT NULL, -- Identify different CLIs
          cli_session_token VARCHAR(255),
          cli_session_token_expiry_timestamp DATETIME,
          cli_verification_token VARCHAR(255),
          cli_wireguard_endpoint VARCHAR(255) NOT NULL,
          cli_wireguard_public_key VARCHAR(255) NOT NULL,
          cli_status BOOLEAN DEFAULT TRUE, -- Active by default
          PRIMARY KEY (cli_id)
        )
      """.update.run

    // Use the transactor properly
    SqlDB.runSchemaQuery(createTableQuery, "database")
  }
}
