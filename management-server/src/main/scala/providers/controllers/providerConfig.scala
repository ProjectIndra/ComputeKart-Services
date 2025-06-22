package providers.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import providers.ProviderDetailsRepository
import java.time.LocalDateTime
import providers.ProviderService

import middleware.BaseController

case class UpdateConfigRequest(
  providerId: String,
  providerAllowedRam: Int,
  providerAllowedVcpu: Int,
  providerAllowedStorage: Int,
  providerAllowedVms: Int,
  providerAllowedNetworks: Int
)

object ProviderConfigController extends BaseController {
  
  def getConfig: Route = path("getConfig") {
    post {
      entity(as[Map[String, String]]) { request =>
        val managementServerVerificationToken = request.get("management_server_verification_token")

        if (managementServerVerificationToken.isEmpty) {
          complete((400, Map("error" -> "Token is required").asJson))
        } else {
          val result = for {
            provider <- ProviderDetailsRepository.getProviderByToken(managementServerVerificationToken.get)
            providerId <- IO.fromOption(provider.map(_.providerId))(new RuntimeException("Invalid Token"))

            providerConf <- ProviderDetailsRepository.getProviderConf(providerId)
            config <- IO.fromOption(providerConf)(new RuntimeException("Provider configuration not found"))
          } yield Map(
            "max_ram" -> config.providerAllowedRam,
            "max_cpu" -> config.providerAllowedVcpu,
            "max_disk" -> config.providerAllowedStorage,
            "max_vms" -> config.providerAllowedVms,
            "max_networks" -> config.providerAllowedNetworks
          )

          onComplete(result.attempt.unsafeToFuture()) {
            case scala.util.Success(Right(response)) =>
              complete((200, response.asJson))
            case scala.util.Success(Left(error)) =>
              complete((500, Map("error" -> error.getMessage).asJson))
            case scala.util.Failure(exception) =>
              complete((500, Map("error" -> exception.getMessage).asJson))
          }
        }
      }
    }
  }

  def updateConfig: Route = path("update_config") {
    post {
      entity(as[UpdateConfigRequest]) { request =>
        val providerId = request.providerId
        val providerAllowedRam = request.providerAllowedRam
        val providerAllowedVcpu = request.providerAllowedVcpu
        val providerAllowedStorage = request.providerAllowedStorage
        val providerAllowedVms = request.providerAllowedVms
        val providerAllowedNetworks = request.providerAllowedNetworks

        if (
          request.providerId.isEmpty ||
          request.providerAllowedRam <= 0 ||
          request.providerAllowedVcpu <= 0 ||
          request.providerAllowedStorage <= 0 ||
          request.providerAllowedVms <= 0 ||
          request.providerAllowedNetworks <= 0
        ) {
          complete((400, Map("error" -> "Missing or invalid required fields").asJson))
        } else {
          val result = for {
            provider <- ProviderDetailsRepository.fetchFullProviderDetails(providerId)
            FullProviderDetails <- IO.fromOption(provider)(new RuntimeException("Provider not found"))

            // Validate used specs against allowed specs
            _ <- IO.raiseWhen(FullProviderDetails.providerUsedRam.exists(_.toInt > providerAllowedRam))(
              new RuntimeException("More RAM already being used by clients than allowed.")
            )
            _ <- IO.raiseWhen(FullProviderDetails.providerUsedVcpu.exists(_.toInt > providerAllowedVcpu))(
              new RuntimeException("More VCPU already being used by clients than allowed.")
            )
            _ <- IO.raiseWhen(FullProviderDetails.providerUsedStorage.exists(_.toInt > providerAllowedStorage))(
              new RuntimeException("More Storage already being used by clients than allowed.")
            )
            _ <- IO.raiseWhen(FullProviderDetails.providerUsedNetworks.exists(_.toInt > providerAllowedNetworks))(
              new RuntimeException("More Networks already being used by clients than allowed.")
            )
            _ <- IO.raiseWhen(FullProviderDetails.providerUsedVms.exists(_.toInt > providerAllowedVms))(
              new RuntimeException("More VMs already being used by clients than allowed.")
            )

            // Send request to provider
            response <- ProviderService.sendUpdateRequestToProvider(
              FullProviderDetails.providerUrl,
              FullProviderDetails.managementServerVerificationToken.getOrElse(""),
              providerAllowedRam,
              providerAllowedVcpu,
              providerAllowedStorage,
              providerAllowedVms,
              providerAllowedNetworks
            )
            _ <- response match {
              case Left(errorMessage) =>
                IO.raiseError(new RuntimeException(s"Failed to update provider configuration: $errorMessage"))
              case Right(successMessage) =>
                IO.unit
            }

            // Update provider configuration in the database
            updateResult <- ProviderDetailsRepository.updateProviderConf(
              providerId,
              providerAllowedRam,
              providerAllowedVcpu,
              providerAllowedStorage,
              providerAllowedVms,
              providerAllowedNetworks
            )
          } yield updateResult

          onComplete(result.attempt.unsafeToFuture()) {
            case scala.util.Success(_) =>
              complete((200, Map("message" -> "Provider configuration updated successfully").asJson))
            case scala.util.Failure(exception) =>
              complete((500, Map("error" -> exception.getMessage).asJson))
          }
        }
      }
    }
  }
}
