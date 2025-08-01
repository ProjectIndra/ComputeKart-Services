package tunnels

import cats.effect.IO
import doobie._
import doobie.implicits._
import java.util.UUID

import main.SqlDB

case class TunnelDetails(
  userId: String,
  username: String
)

case class TunnelDetails2(
  tunnelNo: Int,
  username: String
)

object TunnelDetailsRepository {

  /** Creates a new tunnel and returns the number of rows affected and the session token. */
  def createNewTunnel(tunnelDetails: TunnelDetails): IO[Either[Throwable, String]] = {
    val sessionToken = UUID.randomUUID().toString

    val query =
      sql"""
        INSERT INTO tunnels (user_id, username, session_token)
        VALUES (${tunnelDetails.userId}, ${tunnelDetails.username}, ${sessionToken})
      """.update.run

    SqlDB.runUpdateQuery(query, "Create New Tunnel").map {
      case Right(_) => Right(sessionToken) // Return the session token
      case Left(error) => Left(error)
    }
  }

  /** Verifies a tunnel token and retrieves the corresponding tunnel details. */
  def verifyTunnelToken(sessionToken: String): IO[Either[Throwable, TunnelDetails2]] = {
    val query =
      sql"""
        SELECT tunnel_no, username
        FROM tunnels
        WHERE session_token = $sessionToken
      """.query[TunnelDetails2].option

    SqlDB.runQueryEither(query, "Verify Tunnel Token")
  }
}
