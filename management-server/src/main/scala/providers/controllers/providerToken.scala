package providers.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import providers.ProviderDetailsRepository
import java.util.UUID

import middleware.BaseController
import utils.TempTokenTransaction._
import utils.models.TempTokenModel._

case class ProviderRequest(
  providerVerificationToken: String,
  providerAllowedVms: Int,
  providerAllowedNetworks: Int,
  providerAllowedRam: Int,
  providerAllowedVcpu: Int,
  providerAllowedStorage: Int,
  providerRamCapacity: Int,
  providerVcpuCapacity: Int,
  providerStorageCapacity: Int,
  providerUrl: String
)

case class ProviderWholeDB(
  providerId: String,
  providerRamCapacity: Int,
  providerVcpuCapacity: Int,
  providerStorageCapacity: Int,
  providerUrl: String,
  managementServerVerificationToken: String,
  providerAllowedRam: Int,
  providerAllowedVcpu: Int,
  providerAllowedStorage: Int,
  providerAllowedVms: Int,
  providerAllowedNetworks: Int
)

object ProviderTokenController extends BaseController {

  def getProviderVerificationToken: Route = path("getProviderVerificationToken") {
    post {
      uiLoginRequired { user =>
        val userId = user.get("user_id")
        val providerId = user.getOrElse("provider_id", "").toString

        userId match {
          case Some(id) =>
            val token = UUID.randomUUID().toString
            val detailsMap = Map.empty[String, String]
            val result = if (providerId.nonEmpty) {
              createTempToken(token, id, detailsMap.asJson, Some(providerId)).unsafeToFuture()
            } else {
              createTempToken(token, id, detailsMap.asJson).unsafeToFuture()
            }

            onComplete(result) {
              case scala.util.Success(updatedRows) if updatedRows > 0 =>
                complete((200, Map("cli_verification_token" -> token).asJson))
              case scala.util.Success(_) =>
                complete((404, Map("error" -> "User not found").asJson))
              case scala.util.Failure(ex) =>
                complete((500, Map("error" -> s"Database error: ${ex.getMessage}").asJson))
            }

          case None =>
            complete((400, Map("error" -> "User ID is missing").asJson))
        }
      }
    }
  }

  def verifyProviderToken: Route = path("verifyProviderToken") {
    post {
      entity(as[ProviderRequest]) { providerRequest =>
        val providerVerificationToken = providerRequest.providerVerificationToken
        val result = verifyTempTokenAndGetDetails(providerVerificationToken).unsafeToFuture()

        onComplete(result) {
          case scala.util.Success(Some((userId, None))) =>
            handleNewProvider(providerRequest)

          case scala.util.Success(Some((userId, Some(providerId)))) =>
            handleExistingProvider(providerRequest, providerId)

          case scala.util.Success(None) =>
            complete((400, Map("error" -> "Invalid or expired provider verification token").asJson))

          case scala.util.Failure(ex) =>
            complete((500, Map("error" -> s"Database error: ${ex.getMessage}").asJson))
        }
      }
    }
  }

  private def handleNewProvider(providerRequest: ProviderRequest): Route = {
    val newProviderId = UUID.randomUUID().toString
    val managementServerVerificationToken = UUID.randomUUID().toString
    val providerWholeDB = ProviderWholeDB(
      newProviderId,
      providerRequest.providerRamCapacity,
      providerRequest.providerVcpuCapacity,
      providerRequest.providerStorageCapacity,
      providerRequest.providerUrl,
      managementServerVerificationToken,
      providerRequest.providerAllowedRam,
      providerRequest.providerAllowedVcpu,
      providerRequest.providerAllowedStorage,
      providerRequest.providerAllowedVms,
      providerRequest.providerAllowedNetworks
    )

    val createProviderDetailsIO = createProviderDetails(providerWholeDB).unsafeToFuture()

    onComplete(createProviderDetailsIO) {
      case scala.util.Success(Right(_)) =>
        complete((200, Map("management_server_verification_token" -> managementServerVerificationToken).asJson))
      case scala.util.Success(Left(error)) =>
        complete((500, Map("error" -> s"Failed to create provider details: $error").asJson))
      case scala.util.Failure(ex) =>
        complete((500, Map("error" -> s"Database error: ${ex.getMessage}").asJson))
    }
  }

  private def handleExistingProvider(providerRequest: ProviderRequest, providerId: String): Route = {
    val managementServerVerificationToken = UUID.randomUUID().toString
    val providerWholeDB = ProviderWholeDB(
      providerId,
      providerRequest.providerRamCapacity,
      providerRequest.providerVcpuCapacity,
      providerRequest.providerStorageCapacity,
      providerRequest.providerUrl,
      managementServerVerificationToken,
      providerRequest.providerAllowedRam,
      providerRequest.providerAllowedVcpu,
      providerRequest.providerAllowedStorage,
      providerRequest.providerAllowedVms,
      providerRequest.providerAllowedNetworks
    )

    val updateProviderDetailsIO = updateProviderDetails(providerWholeDB).unsafeToFuture()

    onComplete(updateProviderDetailsIO) {
      case scala.util.Success(Right(_)) =>
        complete((200, Map("management_server_verification_token" -> managementServerVerificationToken).asJson))
      case scala.util.Success(Left(error)) =>
        complete((500, Map("error" -> s"Failed to update provider details: $error").asJson))
      case scala.util.Failure(ex) =>
        complete((500, Map("error" -> s"Database error: ${ex.getMessage}").asJson))
    }
  }

  private def createProviderDetails(providerWholeDB: ProviderWholeDB): IO[Either[String, Unit]] = {
    for {
      insertProvider <- ProviderDetailsRepository.insertProviderDetails(
        providerWholeDB.providerId,
        s"${UUID.randomUUID()}",
        "active",
        providerWholeDB.providerRamCapacity,
        providerWholeDB.providerVcpuCapacity,
        providerWholeDB.providerStorageCapacity,
        providerWholeDB.providerUrl,
        providerWholeDB.managementServerVerificationToken
      )
      insertProviderConf <- ProviderDetailsRepository.insertProviderConf(
        providerWholeDB.providerId,
        providerWholeDB.providerAllowedRam,
        providerWholeDB.providerAllowedVcpu,
        providerWholeDB.providerAllowedStorage,
        providerWholeDB.providerAllowedVms,
        providerWholeDB.providerAllowedNetworks
      )
    } yield for {
      _ <- insertProvider
      _ <- insertProviderConf
    } yield ()
  }

  private def updateProviderDetails(providerWholeDB: ProviderWholeDB): IO[Either[String, Unit]] = {
    for {
      updateProvider <- ProviderDetailsRepository.updateProviderDetails(
        providerWholeDB.providerId,
        providerWholeDB.providerRamCapacity,
        providerWholeDB.providerVcpuCapacity,
        providerWholeDB.providerStorageCapacity,
        providerWholeDB.providerUrl,
        "active",
        providerWholeDB.managementServerVerificationToken
      )
      updateProviderConf <- ProviderDetailsRepository.updateProviderConf(
        providerWholeDB.providerId,
        providerWholeDB.providerAllowedRam,
        providerWholeDB.providerAllowedVcpu,
        providerWholeDB.providerAllowedStorage,
        providerWholeDB.providerAllowedVms,
        providerWholeDB.providerAllowedNetworks
      )
    } yield for {
      _ <- updateProvider
      _ <- updateProviderConf
    } yield ()
  }
}