package vms

import java.time.LocalDateTime
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import doobie.implicits._
import doobie.util.transactor.Transactor
import main.SqlDB

case class VmStatus(
    vmId: String,
    vmName: String,
    status: String,
    providerId: String,
    clientUserId: String,
    vmDeleted: Boolean = false,
    vmDeletedAt: Option[LocalDateTime] = None
)

object VmStatusRepository {

    def validateVmId(vmId: String, userId: String): IO[Either[Throwable, Unit]] = {
    val query =
      sql"""
        SELECT COUNT(*)
        FROM vm_details
        WHERE vm_id = $vmId AND client_user_id = $userId
      """.query[Int].unique

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(count) if count > 0 => Right(())
        case Right(_) =>
          Left(new RuntimeException(s"VM ID $vmId does not exist for user $userId"))
        case Left(e) =>
          println(s"Error validating VM ID: ${e.getMessage}")
          Left(e)
      }
    }
  }

  def markVmAsDeleted(vmId: String): IO[Either[Throwable, Unit]] = {
    val query =
      sql"""
        UPDATE vm_status
        SET vm_deleted = true, vm_deleted_at = ${java.sql.Timestamp.valueOf(LocalDateTime.now())}
        WHERE vm_id = $vmId
      """.update.run

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(_) => Right(())
        case Left(e) =>
          println(s"Error marking VM as deleted: ${e.getMessage}")
          Left(e)
      }
    }
  }

  def insertVmStatus(vmStatus: VmStatus): IO[Either[Throwable, Unit]] = {
    val query =
      sql"""
          INSERT INTO vm_status (
            vm_id, vm_name, status, provider_id, client_user_id, vm_deleted, vm_deleted_at
          ) VALUES (
            ${vmStatus.vmId}, ${vmStatus.vmName}, ${vmStatus.status}, ${vmStatus.providerId},
            ${vmStatus.clientUserId}, ${vmStatus.vmDeleted}, ${vmStatus.vmDeletedAt.map(java.sql.Timestamp.valueOf)}
          )
      """.update.run

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(_) => Right(())
        case Left(e) =>
          println(s"Error inserting VM status: ${e.getMessage}")
          Left(e)
      }
    }
  }
}

