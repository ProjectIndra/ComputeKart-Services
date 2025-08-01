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
import tunnels.TunnelDetailsRepository
import tunnels.TunnelDetails
import users.UserDetailsRepository.getClientDetails

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
  userId: String,
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
        val userIdOpt = user.get("user_id")
        val providerId = user.getOrElse("provider_id", "").toString

        userIdOpt match {
          case Some(userId) =>
            val token = UUID.randomUUID().toString
            val detailsMap = Map.empty[String, String]
            val resultIO = if (providerId.nonEmpty) {
              createTempToken(token, userId, detailsMap.asJson, Some(providerId))
            } else {
              createTempToken(token, userId, detailsMap.asJson)
            }

            onComplete(resultIO.unsafeToFuture()) {
              case scala.util.Success(Right(_)) =>
                complete((200, Map("cli_verification_token" -> token).asJson))
              case scala.util.Success(Left(error)) =>
                complete((500, Map("error" -> s"Failed to create temp token: ${error.getMessage}").asJson))
              case scala.util.Failure(ex) =>
                complete((500, Map("error" -> s"Unexpected error: ${ex.getMessage}").asJson))
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
        val resultIO = verifyTempTokenAndGetDetails(providerVerificationToken)

        onComplete(resultIO.unsafeToFuture()) {
          case scala.util.Success(Right((userId, None))) =>
            handleNewProvider(providerRequest, userId)

          case scala.util.Success(Right((userId, Some(providerId)))) =>
            handleExistingProvider(providerRequest, providerId, userId)

          case scala.util.Success(Left(error)) =>
            complete((400, Map("error" -> s"Invalid or expired provider verification token: ${error.getMessage}").asJson))

          case scala.util.Failure(ex) =>
            complete((500, Map("error" -> s"Unexpected error: ${ex.getMessage}").asJson))
        }
      }
    }
  }

  private def handleNewProvider(providerRequest: ProviderRequest, userId: String): Route = {
    val newProviderId = UUID.randomUUID().toString
    val managementServerVerificationToken = UUID.randomUUID().toString
    val providerWholeDB = ProviderWholeDB(
      userId,
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

    // Get username from the userId
    val usernameIO = getClientDetails(userId).flatMap {
      case Right(details) => IO.pure(details.username)
      case Left(error)    => IO.raiseError(new RuntimeException(s"Failed to retrieve user details: ${error.getMessage}"))
    }

    // Create provider details and tunnel
    val resultIO = for {
      username <- usernameIO
      _ <- createProviderDetails(providerWholeDB)
      sessionToken <- TunnelDetailsRepository.createNewTunnel(
        TunnelDetails(
          userId = userId,
          username = username
        )
      ).flatMap {
        case Right(output) => IO.pure(output)
        case Left(error)   => IO.raiseError(new RuntimeException(s"Failed to create tunnel: ${error.getMessage}"))
      }
    } yield (managementServerVerificationToken, sessionToken)

    // Handle the result
    onComplete(resultIO.unsafeToFuture()) {
      case scala.util.Success((managementToken, sessionToken)) =>
        complete((200, Map(
          "management_server_verification_token" -> managementToken,
          "tunnel_server_verification_token" -> sessionToken
        ).asJson))
      case scala.util.Failure(ex) =>
        complete((500, Map("error" -> s"Failed to handle new provider: ${ex.getMessage}").asJson))
    }
  }

  private def handleExistingProvider(providerRequest: ProviderRequest, providerId: String, userId: String): Route = {
    val managementServerVerificationToken = UUID.randomUUID().toString
    val providerWholeDB = ProviderWholeDB(
      userId = userId,
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

    val updateProviderDetailsIO = updateProviderDetails(providerWholeDB)

    onComplete(updateProviderDetailsIO.unsafeToFuture()) {
      case scala.util.Success(Right(_)) =>
        complete((200, Map("management_server_verification_token" -> managementServerVerificationToken).asJson))
      case scala.util.Success(Left(error)) =>
        complete((500, Map("error" -> s"Failed to update provider details: ${error.getMessage}").asJson))
      case scala.util.Failure(ex) =>
        complete((500, Map("error" -> s"Unexpected error: ${ex.getMessage}").asJson))
    }
  }

  private def createProviderDetails(providerWholeDB: ProviderWholeDB): IO[Either[Throwable, Unit]] = {
    for {
      insertProvider <- ProviderDetailsRepository.insertProviderDetails(
        providerWholeDB.userId,
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

  private def updateProviderDetails(providerWholeDB: ProviderWholeDB): IO[Either[Throwable, Unit]] = {
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
