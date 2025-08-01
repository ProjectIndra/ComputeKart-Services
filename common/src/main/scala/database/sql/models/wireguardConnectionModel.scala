package vms.models

import cats.effect.IO
import doobie._
import doobie.implicits._

import main.SqlDB

object WireguardConnectionModel {
  def createWireguardConnectionTable(): IO[Int] = {
    val createTableQuery =
      sql"""
        CREATE TABLE IF NOT EXISTS wireguard_connections (
          wireguard_ip VARCHAR(255), -- WireGuard IP address
          wireguard_public_key VARCHAR(255), -- WireGuard public key
          wireguard_status VARCHAR(255), -- Status of the WireGuard connection
          cli_id VARCHAR(255) NOT NULL, -- CLI ID associated with the connection
          PRIMARY KEY (cli_id)
        )
      """.update.run

    // Use the transactor properly
    SqlDB.runSchemaQuery(createTableQuery, "creating tables")

  }
}
