package providers.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import providers.ProviderDetailsRepository
import users.UserDetailsRepository
import java.util.UUID

import utils.CryptoUtils._

import middleware.BaseController
import tunnels.TunnelDetailsRepository
import tunnels.TunnelDetails

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

object VerifyProviderTokenController extends BaseController {

  def verifyProviderToken: Route = path("verifyProviderToken") {
    post {
      entity(as[ProviderRequest]) { request =>
        val result = for {
          // Decode the JWT token
          decodedToken <- decodeJwt(request.providerVerificationToken)
          userId <- IO.fromOption(decodedToken.get("user_id"))(new RuntimeException("user_id is missing in token"))
          providerId <- IO.fromOption(decodedToken.get("provider_id"))(new RuntimeException("provider_id is missing in token"))

          // Verify if the user exists and fetch username
          userDetails <- UserDetailsRepository.getClientDetails(userId).flatMap {
            case Right(Some(user)) => IO.pure(user)
            case Right(None) => IO.raiseError(new RuntimeException("Invalid User"))
            case Left(e) => IO.raiseError(e)
          }
          username = userDetails.username 

          // Get provider details
          providerDetails <- ProviderDetailsRepository.getProviderDetails(providerId)
          managementServerVerificationToken = UUID.randomUUID().toString

          // Create new provider details
          newProviderDetails = ProviderWholeDB(
            providerId,
            request.providerRamCapacity,
            request.providerVcpuCapacity,
            request.providerStorageCapacity,
            request.providerUrl,
            managementServerVerificationToken,
            request.providerAllowedRam,
            request.providerAllowedVcpu,
            request.providerAllowedStorage,
            request.providerAllowedVms,
            request.providerAllowedNetworks
          )

          // Update or create provider details
          providerResult <- providerDetails match {
            case Right(_) =>
              updateProviderDetails(newProviderDetails)
            case Left(_) =>
              createProviderDetails(newProviderDetails)
          }

          // Create a new tunnel and get the session token
          tunnelResult <- TunnelDetailsRepository.createNewTunnel(TunnelDetails(userId, username))
        } yield providerResult.map(_ => Map(
          "message" -> "Provider token verified successfully",
          "management_server_verification_token" -> managementServerVerificationToken,
          "tunnel_server_verification_token" -> tunnelResult._2
        ))

        onComplete(result.unsafeToFuture()) {
          case scala.util.Success(Right(response)) =>
            complete((200, response.asJson))
          case scala.util.Success(Left(error)) =>
            complete((500, Map("error" -> error).asJson))
          case scala.util.Failure(exception) =>
            complete((500, Map("error" -> exception.getMessage).asJson))
        }
      }
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
