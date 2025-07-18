package tunnels

import cats.effect.IO
import doobie._
import doobie.implicits._
import java.util.UUID

import main.SqlDB

case class TunnelDetails(
  userId: String,
  username: String,
)

case class TunnelDetails2(
  tunnelNo: Int,
  username: String,
)

object TunnelDetailsRepository {

  def createNewTunnel(tunnelDetails: TunnelDetails): IO[(Int, String)] = {
    val sessionToken = UUID.randomUUID().toString

    val query =
      sql"""
        INSERT INTO tunnels (user_id, username, session_token)
        VALUES (${tunnelDetails.userId}, ${tunnelDetails.username}, ${sessionToken})
      """.update.run

    SqlDB.transactor.use { xa =>
      query.transact(xa).map(rowsAffected => (rowsAffected, sessionToken))
    }
  }

  def verifyTunnelToken(sessionToken: String): IO[Option[TunnelDetails2]] = {
    val query =
      sql"""
        SELECT tunnel_no, username
        FROM tunnels
        WHERE session_token = $sessionToken
      """.query[TunnelDetails2].option

    SqlDB.transactor.use { xa =>
      query.transact(xa)
    }
  }
}