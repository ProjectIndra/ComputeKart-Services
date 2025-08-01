package providers.models

import cats.effect.IO
import doobie._
import doobie.implicits._
import main.SqlDB

object ProviderConfModel {
  def createProviderConfTable(): IO[Int] = {
    val createTableQuery =
      sql"""
        CREATE TABLE IF NOT EXISTS provider_conf (
          provider_id VARCHAR(255) NOT NULL, -- Unique identifier for the provider
          provider_allowed_ram VARCHAR(255) NOT NULL, -- Allowed RAM for the provider
          provider_allowed_vcpu VARCHAR(255) NOT NULL, -- Allowed vCPU for the provider
          provider_allowed_storage VARCHAR(255) NOT NULL, -- Allowed storage for the provider
          provider_allowed_vms VARCHAR(255) NOT NULL, -- Allowed number of VMs
          provider_allowed_networks VARCHAR(255) NOT NULL, -- Allowed number of networks
          PRIMARY KEY (provider_id)
        )
      """.update.run

    // Use the transactor properly
    SqlDB.runSchemaQuery(createTableQuery, "creating tables")

  }
}
