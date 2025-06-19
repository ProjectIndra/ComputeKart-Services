package vms.models

import cats.effect.IO
import doobie._
import doobie.implicits._

import main.SqlDB

object VmDetailsModel {
  def createVmDetailsTable(): IO[Int] = {
    val createTableQuery =
      sql"""
        CREATE TABLE IF NOT EXISTS vm_details (
          provider_user_id VARCHAR(255) NOT NULL, -- ID of the provider user
          client_user_id VARCHAR(255) NOT NULL, -- ID of the client user
          vm_id VARCHAR(255) NOT NULL, -- Unique identifier for the VM
          vm_name VARCHAR(255), -- Reference name for the user
          provider_id VARCHAR(255), -- ID of the provider server
          provider_name VARCHAR(255) NOT NULL, -- Name of the provider
          vcpu VARCHAR(255) NOT NULL, -- Number of virtual CPUs
          ram VARCHAR(255) NOT NULL, -- RAM size
          storage VARCHAR(255) NOT NULL, -- Storage size
          vm_image_type VARCHAR(255) NOT NULL, -- Type of VM image
          wireguard_connection_details JSON, -- Array of JSON objects for WireGuard connection details
          internal_vm_name VARCHAR(255), -- Internal reference name
          vm_created_at DATETIME NOT NULL, -- Timestamp when the VM was created
          PRIMARY KEY (vm_id)
        )
      """.update.run

    // Use the transactor properly
    SqlDB.transactor.use { xa =>
      createTableQuery.transact(xa)
    }
  }
}