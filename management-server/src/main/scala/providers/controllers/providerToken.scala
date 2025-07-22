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
import utils.TempTokenTransaction._

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

object ProviderTokenController extends BaseController {

  /*
   * This function will be called from ui/other user interfaces to get a provider verification token.
   * this provider verification token will be used to 
   *  i> verify the new provider creation request
   *  ii> verify already existing provider details ( get a new management server verification token for the provider )
   * 
   * every time we need to verify the provider , we generate a new provider verification token.
   * we then send this to user using his/her interface which can be ui/other user interfaces.
   * then the user will paste the verification token in the provider server env as provider_server_init_token
   * which will be then sent to the management server back to verify the provider.
   * which after verification should
   *   i> create a new provider details in the management server
   *   ii> or update the existing provider details in the management server with new details ( management server verification token and tunnel details )
   */
  def getProviderVerificationToken: Route = path("getProviderVerificationToken") {
    post {
      uiLoginRequired { user =>
        val userId = user.get("user_id")
        val providerId = user.getOrElse("provider_id", None) // this only comes when the user is reauthenticating an existing provider

        if (userId.isEmpty) {
          complete((400, Map("error" -> "User IDis missing").asJson))
        } else {

          val token = UUID.randomUUID().toString
          val detailsMap = Map.empty[String, String]
          val result = createTempToken(token, userId, detailsMap, providerId)

          onComplete(result) {
            case scala.util.Success(updatedRows) if updatedRows > 0 =>
              complete((200, Map("cli_verification_token" -> token).asJson))
            case scala.util.Success(_) =>
              complete((404, Map("error" -> "User not found").asJson))
            case scala.util.Failure(ex) =>
              complete((500, Map("error" -> s"Database error: ${ex.getMessage}").asJson))
          }
        }
      }
    }
  }

  /*
   * This function will be called from provider server to verify the provider token.
   * once we get the token we check for it in the temprovary token table.
   * if the token is valid and under correct time and correct user_id , we remove the token and create a new management server verification token.
   * then we update any details of the provider in the management server.
   * if the token is not valid or expired we return an error.
   */
  def verifyProviderToken: Route = path("verifyProviderToken") {
    post {
      entity(as[ProviderRequest]) { providerRequest =>
        val providerVerificationToken = providerRequest.providerVerificationToken

        // verify the token in the table
        val result = verifyTempTokenAndGetDetails(providerVerificationToken)

        onComplete(result) {

          // handle the result of the token verification , when providerVerificationToken is valid and new provider is being created
          case scala.util.Success(Some((userId,None, attributes))) => {
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
            val createProviderDetailsIO = createProviderDetails(providerWholeDB)
            onComplete(createProviderDetailsIO.unsafeToFuture()) {
              case scala.util.Success(Right(_)) =>
                complete((200, Map("management_server_verification_token" -> managementServerVerificationToken).asJson))
              case scala.util.Success(Left(error)) =>
                complete((500, Map("error" -> s"Failed to create provider details: $error").asJson))
              case scala.util.Failure(ex) =>
                complete((500, Map("error" -> s"Database error: ${ex.getMessage}").asJson))
            }
          }

          // handle the result of the token verification , when providerVerificationToken is valid and existing provider is being updated
          case scala.util.Success(Some((userId, Some(providerId), attributes))) => {
            // if the user is already a provider , we update the provider details
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
            val updateProviderDetailsIO = updateProviderDetails(providerWholeDB)
            onComplete(updateProviderDetailsIO.unsafeToFuture()) {
              case scala.util.Success(Right(_)) =>
                complete((200, Map("management_server_verification_token" -> managementServerVerificationToken).asJson))
              case scala.util.Success(Left(error)) =>
                complete((500, Map("error" -> s"Failed to update provider details: $error").asJson))
              case scala.util.Failure(ex) =>
                complete((500, Map("error" -> s"Database error: ${ex.getMessage}").asJson))
            }
          }
          case scala.util.Success(None) =>
            complete((400, Map("error" -> "Invalid or expired provider verification token").asJson))
          case scala.util.Failure(ex) =>
            complete((500, Map("error" -> s"Database error: ${ex.getMessage}").asJson))
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