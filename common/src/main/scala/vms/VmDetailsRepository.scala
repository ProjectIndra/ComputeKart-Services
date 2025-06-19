package vms

import java.time.LocalDateTime
import provider.{ProviderDetails, ProviderService}
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import doobie.implicits._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}
import scalaj.http.HttpResponse
import scalaj.http._

import main.SqlDB

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
    providerId: String,
)

object VmDetailsRepository {
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
}
