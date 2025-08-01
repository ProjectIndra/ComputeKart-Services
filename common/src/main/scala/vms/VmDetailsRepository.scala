package vms

import java.time.LocalDateTime
import cats.effect.IO
import java.sql.Timestamp
import cats.effect.unsafe.implicits.global
import doobie.implicits._
import doobie.util.meta.Meta
import doobie.generic.auto._
import doobie.util.transactor.Transactor
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import doobie.implicits._
import doobie.util.Read
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}
import scalaj.http.HttpResponse
import scalaj.http._

import main.SqlDB
import providers.{ProviderDetails, ProviderService}

case class VmDetails(
  clientUserId: String,
  vcpus: Int,
  ram: Int,
  storage: Int,
  vmImageType: String,
  vmName: String,
  internalVmName: String,
  provider: ProviderDetails,
  status: String,
  createdAt: LocalDateTime = LocalDateTime.now(),
  providerId: String
)

object VmDetailsRepository {

  implicit val localDateTimeMeta: Meta[LocalDateTime] =
    Meta[Timestamp].imap(_.toLocalDateTime)(Timestamp.valueOf)

  def getVmDetailsByName(vmName: String, userId: String): IO[Either[Throwable, (String, String, String)]] = {
    val query =
      sql"""
      SELECT vm_id, provider_id, internal_vm_name
      FROM vm_details
      WHERE vm_name = $vmName AND client_user_id = $userId AND vm_deleted = false
    """.query[(String, String, String)].option

    SqlDB.runQueryEither(query, "database")
  }

  def getInternalVmName(vmId: String, userId: String): IO[Either[Throwable, String]] = {
    val query =
      sql"""
        SELECT internal_vm_name
        FROM vm_details
        WHERE vm_id = $vmId AND client_user_id = $userId
      """.query[String].unique

    SqlDB.runQuery(query, "database")
  }

  def insertVmDetails(vmDetails: VmDetails): IO[Either[Throwable, Unit]] = {
    val query =
      sql"""
        INSERT INTO vm_details (
        provider_user_id, client_user_id, vm_id, vm_name, internal_vm_name, 
        vm_image_type, provider_id, provider_name, ram, vcpus, storage, 
        vm_creation_time
        ) VALUES (
        ${vmDetails.provider.providerUserId}, ${vmDetails.clientUserId}, ${vmDetails.providerId}, 
        ${vmDetails.vmName}, ${vmDetails.internalVmName}, ${vmDetails.vmImageType}, 
        ${vmDetails.providerId}, ${vmDetails.provider.providerName}, ${vmDetails.ram}, 
        ${vmDetails.vcpus}, ${vmDetails.storage}, ${java.sql.Timestamp.valueOf(vmDetails.createdAt)}
        )
      """.update.run

    SqlDB.runUpdateQuery(query, "database")
  }

  def isVmActive(vmId: String, clientUserId: String): IO[Either[Throwable, Boolean]] = {
    val query =
      sql"""
        SELECT COUNT(*)
        FROM vm_details vd
        JOIN vm_status vs ON vd.vm_id = vs.vm_id
        WHERE vd.vm_id = $vmId
        AND vd.client_user_id = $clientUserId
        AND (vs.status = 'active' OR vs.status = 'inactive')
        AND vd.vm_deleted = false
      """.query[Int].unique

    SqlDB.runCountQuery(query, "database")
  }

  def isVmNameExists(clientUserId: String, vmName: String): IO[Either[Throwable, Boolean]] = {
    val query =
      sql"""
          SELECT COUNT(*)
          FROM vm_details
          WHERE client_user_id = $clientUserId AND vm_name = $vmName
      """.query[Int].unique

    SqlDB.runCountQuery(query, "database")
  }

  def getVmClients(providerUserId: String): IO[Either[Throwable, List[(String, String, String)]]] = {
    val query =
      sql"""
        SELECT vd.client_user_id, vd.vm_id, vs.status
        FROM vm_details vd
        JOIN vm_status vs ON vd.vm_id = vs.vm_id
        WHERE vd.provider_user_id = $providerUserId
      """.query[(String, String, String)].to[List]

    SqlDB.runQuery(query, "database")
  }

  def getAllVms(userId: String): IO[Either[Throwable, List[(String, String, String, String, String, String, String, String, String, LocalDateTime)]]] = {
    val query =
      sql"""
      SELECT vd.client_user_id, vd.vcpu, vd.ram, vd.storage, vd.vm_image_type, vd.vm_name, 
             vd.internal_vm_name, vd.provider_id, vs.status, vd.vm_created_at
      FROM vm_details vd
      JOIN vm_status vs ON vd.vm_id = vs.vm_id
      WHERE vd.client_user_id = $userId
    """.query[
        (String, String, String, String, String, String, String, String, String, LocalDateTime)
      ].to[List]

    SqlDB.runQuery(query, "database")
  }

  def getAllVmsFiltered(userId: String, vmName: String): IO[Either[Throwable, List[(String, String, String, String, String, String, String, String, String, LocalDateTime)]]] = {
    val query =
      sql"""
      SELECT vd.client_user_id, vd.vcpu, vd.ram, vd.storage, vd.vm_image_type, vd.vm_name, 
             vd.internal_vm_name, vd.provider_id, vs.status, vd.vm_created_at
      FROM vm_details vd
      JOIN vm_status vs ON vd.vm_id = vs.vm_id
      WHERE vd.client_user_id = $userId AND vd.vm_name LIKE %$vmName%
    """.query[
        (String, String, String, String, String, String, String, String, String, LocalDateTime)
      ].to[List]

    SqlDB.runQuery(query, "database")
  }

  def getAllActiveVms(userId: String): IO[Either[Throwable, List[(String, String, String, String, String, String, String, String, String, LocalDateTime)]]] = {
    val query =
      sql"""
      SELECT vd.client_user_id, vd.vcpu, vd.ram, vd.storage, vd.vm_image_type, vd.vm_name, 
             vd.internal_vm_name, vd.provider_id, vs.status, vd.vm_created_at
      FROM vm_details vd
      JOIN vm_status vs ON vd.vm_id = vs.vm_id
      WHERE vd.client_user_id = $userId AND vs.status = 'active'
    """.query[
        (String, String, String, String, String, String, String, String, String, LocalDateTime)
      ].to[List]

    SqlDB.runQuery(query, "database")
  }

}
