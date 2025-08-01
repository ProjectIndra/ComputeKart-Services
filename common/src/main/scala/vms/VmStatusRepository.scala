package vms

import java.time.LocalDateTime
import cats.effect.IO
import doobie.implicits._
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

  implicit val vmStatusRead: doobie.Read[VmStatus] = {
    import doobie.util.Get
    import doobie.util.Read

    implicit val localDateTimeGet: Get[LocalDateTime] =
      Get[java.sql.Timestamp].map(_.toLocalDateTime)

    implicit val optionLocalDateTimeGet: Get[Option[LocalDateTime]] =
      Get[java.sql.Timestamp].map(Option(_)).map(_.map(_.toLocalDateTime))

    Read[(String, String, String, String, String, Boolean, Option[LocalDateTime])].map { case (vmId, vmName, status, providerId, clientUserId, vmDeleted, vmDeletedAt) =>
      VmStatus(vmId, vmName, status, providerId, clientUserId, vmDeleted, vmDeletedAt)
    }
  }

  /** Validates if a VM ID exists for a given user. */
  def validateVmId(vmId: String, userId: String): IO[Either[Throwable, Boolean]] = {
    val query =
      sql"""
        SELECT COUNT(*)
        FROM vm_details
        WHERE vm_id = $vmId AND client_user_id = $userId
      """.query[Int].unique

    SqlDB.runCountQuery(query, "Validate VM ID")
  }

  /** Marks a VM as deleted. */
  def markVmAsDeleted(vmId: String): IO[Either[Throwable, Unit]] = {
    val query =
      sql"""
        UPDATE vm_status
        SET vm_deleted = true, vm_deleted_at = ${java.sql.Timestamp.valueOf(LocalDateTime.now())}
        WHERE vm_id = $vmId
      """.update.run

    SqlDB.runUpdateQuery(query, "Mark VM as Deleted")
  }

  /** Inserts a new VM status. */
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

    SqlDB.runUpdateQuery(query, "Insert VM Status")
  }

  /** Retrieves all active VMs for a user. */
  def allActiveVmsForUser(userId: String): IO[Either[Throwable, List[VmStatus]]] = {
    val query =
      sql"""
        SELECT vm_id, vm_name, status, provider_id, client_user_id, vm_deleted, vm_deleted_at
        FROM vm_status
        WHERE client_user_id = $userId AND vm_deleted = false AND status IN ('active', 'pending')
      """.query[VmStatus].to[List]

    SqlDB.runQuery(query, "Get All Active VMs for User")
  }

  /** Retrieves all VMs for a user. */
  def allVmsForUser(userId: String): IO[Either[Throwable, List[VmStatus]]] = {
    val query =
      sql"""
        SELECT vm_id, vm_name, status, provider_id, client_user_id, vm_deleted, vm_deleted_at
        FROM vm_status
        WHERE client_user_id = $userId
      """.query[VmStatus].to[List]

    SqlDB.runQuery(query, "Get All VMs for User")
  }
}
