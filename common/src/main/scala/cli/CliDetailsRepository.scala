package cli

import cats.effect.IO
import doobie.implicits._
import doobie.util.transactor.Transactor
import main.SqlDB

case class CliSessionDetails(user_id: String, cli_id: String, cli_wireguard_endpoint: String, cli_wireguard_public_key: String, cli_status: Boolean, cli_session_token_expiry_timestamp: String)

object CliDetailsRepository {

  def findCliSessionByToken(token: String): IO[Option[CliSessionDetails]] = {
    val query =
      sql"""
        SELECT user_id, cli_id, cli_wireguard_endpoint, cli_wireguard_public_key, cli_status, cli_session_token_expiry_timestamp
        FROM cli_sessions
        WHERE cli_session_token = $token AND cli_status = true
      """.query[CliSessionDetails].option

    SqlDB.transactor.use(xa => query.transact(xa))
  }

  def getAllCliSessionDetails(userId: String): IO[List[CliSessionDetails]] = {
    val query =
      sql"""
        SELECT user_id, cli_id, cli_wireguard_endpoint, cli_wireguard_public_key, cli_status, cli_session_token_expiry_timestamp
        FROM cli_sessions
        WHERE user_id = $userId AND cli_status = true
      """.query[CliSessionDetails].to[List]

    SqlDB.transactor.use(xa => query.transact(xa))
  }

  def deleteCliSession(userId: String, cliId: String): IO[Int] = {
    val updateQuery =
      sql"""
        UPDATE cli_sessions
        SET cli_status = false
        WHERE user_id = $userId AND cli_id = $cliId
      """.update.run

    SqlDB.transactor.use(xa => updateQuery.transact(xa))
  }
  def updateCliVerificationToken(userId: String, cliVerificationToken: String): IO[Int] = {
    val query =
      sql"""
        UPDATE users
        SET cli_verification_token = $cliVerificationToken
        WHERE user_id = $userId
      """.update.run

    SqlDB.transactor.use(xa => query.transact(xa))
  }

  def verifyCliToken(cliVerificationToken: String): IO[Option[String]] = {
    val query =
      sql"""
        SELECT user_id
        FROM users
        WHERE cli_verification_token = $cliVerificationToken
      """.query[String].option

    SqlDB.transactor.use(xa => query.transact(xa))
  }

  def insertCliSession(
    userId: String,
    cliId: String,
    wireguardEndpoint: String,
    wireguardPublicKey: String,
    sessionToken: String,
    sessionExpiryTime: String,
    cliVerificationToken: String
  ): IO[Int] = {
    val query =
      sql"""
        INSERT INTO cli_sessions (user_id, cli_id, cli_wireguard_endpoint, cli_wireguard_public_key, cli_status, cli_session_token, cli_session_token_expiry_timestamp, cli_verification_token)
        VALUES ($userId, $cliId, $wireguardEndpoint, $wireguardPublicKey, true, $sessionToken, $sessionExpiryTime, $cliVerificationToken)
      """.update.run

    SqlDB.transactor.use(xa => query.transact(xa))
  }

}
