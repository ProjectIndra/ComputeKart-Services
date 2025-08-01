package providers.models

import cats.effect.IO
import doobie._
import doobie.implicits._
import main.SqlDB

object ProviderModel {
  def createProviderTable(): IO[Int] = {
    val createTableQuery =
      sql"""
        CREATE TABLE IF NOT EXISTS providers (
          user_id VARCHAR(255) NOT NULL, -- User ID of the provider user
          provider_id VARCHAR(255) NOT NULL, -- Unique identifier for the provider
          provider_name VARCHAR(255) NOT NULL, -- Name of the provider
          provider_status VARCHAR(255) NOT NULL, -- Status of the provider
          provider_ram_capacity VARCHAR(255) NOT NULL, -- Total RAM capacity
          provider_vcpu_capacity VARCHAR(255) NOT NULL, -- Total vCPU capacity
          provider_storage_capacity VARCHAR(255) NOT NULL, -- Total storage capacity
          provider_used_ram VARCHAR(255), -- Used RAM
          provider_used_vcpu VARCHAR(255), -- Used vCPU
          provider_used_storage VARCHAR(255), -- Used storage
          provider_used_vms VARCHAR(255), -- Number of used VMs
          provider_used_networks VARCHAR(255), -- Number of used networks
          provider_rating FLOAT, -- Rating of the provider
          provider_url VARCHAR(255), -- URL of the provider
          management_server_verification_token VARCHAR(255), -- Verification token
          PRIMARY KEY (provider_id)
        )
      """.update.run

    // Use the transactor properly
    SqlDB.runSchemaQuery(createTableQuery, "creating tables")

  }
}
