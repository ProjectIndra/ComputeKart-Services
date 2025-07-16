package tunnels

import cats.effect.IO
import doobie._
import doobie.implicits._
import main.SqlDB

case class TunnelDetails(
  user_id : String,
  provider_tunnel_id : String,
  management_server_verification_token : String,
  provider_id : Option[String],
  provider_name : Option[String],
)

object TunnelDetailsRepository {

  def createTunnelDetailsTable(): IO[Int] = {
    val createTableQuery =
      sql"""
        CREATE TABLE IF NOT EXISTS tunnel_details (
          user_id VARCHAR(255) NOT NULL,
          provider_tunnel_id VARCHAR(255) NOT NULL,
          management_server_verification_token VARCHAR(255),
          provider_id VARCHAR(255),
          provider_name VARCHAR(255),
          PRIMARY KEY (provider_tunnel_id)
        )
      """.update.run

    SqlDB.transactor.use { xa =>
      createTableQuery.transact(xa)
    }
  }

}