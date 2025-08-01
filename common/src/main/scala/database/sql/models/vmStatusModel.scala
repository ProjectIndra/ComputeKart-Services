package vms.models

import cats.effect.IO
import doobie._
import doobie.implicits._

import main.SqlDB

object VmStatusModel {
  def createVmStatusTable(): IO[Int] = {
    val createTableQuery =
      sql"""
        CREATE TABLE IF NOT EXISTS vm_status (
          vm_id VARCHAR(255) NOT NULL, -- Unique identifier for the VM
          vm_name VARCHAR(255), -- Name of the VM
          status VARCHAR(255) NOT NULL, -- Status of the VM
          provider_id VARCHAR(255), -- ID of the provider server
          client_user_id VARCHAR(255) NOT NULL, -- ID of the client user
          vm_deleted BOOLEAN DEFAULT FALSE, -- Indicates if the VM is deleted
          vm_deleted_at DATETIME, -- Timestamp when the VM was deleted
          PRIMARY KEY (vm_id)
        )
      """.update.run

    // Use the transactor properly
    SqlDB.runSchemaQuery(createTableQuery, "creating tables")

  }
}
