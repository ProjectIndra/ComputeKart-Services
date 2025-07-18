package tunnels.models

import cats.effect.IO
import doobie._
import doobie.implicits._
import main.SqlDB

object TunnelModel {
  def createTunnelTable(): IO[Int] = {
    val createTableQuery =
      sql"""
        CREATE TABLE IF NOT EXISTS tunnels (
          tunnel_no int NOT NULL AUTO_INCREMENT, -- Auto-incrementing tunnel number
          user_id VARCHAR(255) NOT NULL, -- User ID of the tunnel owner
          username VARCHAR(255) NOT NULL, -- Username of the tunnel owner
          session_token VARCHAR(255) NOT NULL, -- Session token for the tunnel
          PRIMARY KEY (tunnel_no)
        )
      """.update.run
    // Use the transactor properly
    SqlDB.transactor.use { xa =>
      createTableQuery.transact(xa)
    }
  }
}