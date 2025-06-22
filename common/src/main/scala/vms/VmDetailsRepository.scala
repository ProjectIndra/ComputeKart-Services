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

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(Some(vmDetails)) => Right(vmDetails)
        case Right(None) =>
          Left(new RuntimeException(s"VM with name '$vmName' not found for user '$userId'"))
        case Left(e) =>
          println(s"Error fetching VM details by name: ${e.getMessage}")
          Left(e)
      }
    }
  }

  def isVmNameExists(clientUserId: String, vmName: String): IO[Either[Throwable, Boolean]] = {
    val query =
      sql"""
          SELECT COUNT(*)
          FROM vm_details
          WHERE client_user_id = $clientUserId AND vm_name = $vmName
      """.query[Int].unique

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(count) => Right(count > 0)
        case Left(e) =>
          println(s"Error checking VM name existence: ${e.getMessage}")
          Left(e)
      }
    }
  }

  def getInternalVmName(vmId: String, userId: String): IO[Either[Throwable, String]] = {
    val query =
      sql"""
        SELECT internal_vm_name
        FROM vm_details
        WHERE vm_id = $vmId AND client_user_id = $userId
      """.query[String].unique

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(internalVmName) => Right(internalVmName)
        case Left(e) =>
          println(s"Error fetching internal VM name: ${e.getMessage}")
          Left(e)
      }
    }
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

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(_) => Right(())
        case Left(e) =>
          println(s"Error inserting VM details: ${e.getMessage}")
          Left(e)
      }
    }
  }

  def isVmActive(vmId: String, clientUserId: String): IO[Either[Throwable, Boolean]] = {
    val query =
      sql"""
        SELECT COUNT(*)
        FROM vm_status
        WHERE vm_id = $vmId
        AND client_user_id = $clientUserId
        AND (status = 'active' OR status = 'inactive')
        AND vm_deleted = false
      """.query[Int].unique

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(count) => Right(count > 0)
        case Left(e) =>
          println(s"Error checking VM active status: ${e.getMessage}")
          Left(e)
      }
    }
  }

  def getVmClients(providerUserId: String): IO[Either[Throwable, List[(String, String)]]] = {
    val query =
      sql"""
        SELECT client_user_id, vm_id
        FROM vm_details
        WHERE provider_user_id = $providerUserId
      """.query[(String, String)].to[List]

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(clients) => Right(clients)
        case Left(e) =>
          println(s"Error fetching VM clients for provider $providerUserId: ${e.getMessage}")
          Left(e)
      }
    }
  }

  def getAllVms(userId: String): IO[Either[Throwable, List[(String, String, String, String, String, String, String, String, String, LocalDateTime)]]] = {
    val query =
      sql"""
      SELECT client_user_id, vcpu, ram, storage, vm_image_type, vm_name, internal_vm_name,
             provider_id, status, vm_created_at
      FROM vm_details
      WHERE client_user_id = $userId
    """.query[
        (String, String, String, String, String, String, String, String, String, LocalDateTime)
      ].to[List]

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(rows) => Right(rows)
        case Left(e) =>
          println(s"Error fetching VMs: ${e.getMessage}")
          Left(e)
      }
    }
  }

  def getAllActiveVms(userId: String): IO[Either[Throwable, List[(String, String, String, String, String, String, String, String, String, LocalDateTime)]]] = {
    val query =
      sql"""
      SELECT client_user_id, vcpu, ram, storage, vm_image_type, vm_name, internal_vm_name,
             provider_id, status, vm_created_at
      FROM vm_details
      WHERE client_user_id = $userId AND status = 'active'
    """.query[
        (String, String, String, String, String, String, String, String, String, LocalDateTime)
      ].to[List]

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(rows) => Right(rows)
        case Left(e) =>
          println(s"Error fetching active VMs: ${e.getMessage}")
          Left(e)
      }
    }
  }

}
