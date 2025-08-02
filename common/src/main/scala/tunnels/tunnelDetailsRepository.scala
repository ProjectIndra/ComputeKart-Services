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
  def createNewTunnel(tunnelDetails: TunnelDetails): IO[Either[Throwable, (String, String)]] = {
    val sessionToken = UUID.randomUUID().toString

    // First insert without RETURNING clause
    val insertQuery = sql"""
      INSERT INTO tunnels (user_id, username, session_token)
      VALUES (${tunnelDetails.userId}, ${tunnelDetails.username}, $sessionToken)
    """.update

    // Then get the last inserted ID
    val getLastIdQuery = sql"SELECT LAST_INSERT_ID()".query[Int].unique

    // Combine both operations
    val transaction = for {
      _ <- insertQuery.run
      id <- getLastIdQuery
    } yield id

    SqlDB.runQuery(transaction, "Create New Tunnel").map {
      case Right(tunnelNo) => 
        Right((tunnelNo.toString, sessionToken))
      case Left(error) => 
        Left(error)
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
