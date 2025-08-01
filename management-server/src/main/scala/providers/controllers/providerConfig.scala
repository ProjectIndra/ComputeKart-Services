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
            providerEither <- ProviderDetailsRepository.getProviderByToken(managementServerVerificationToken.get)
            provider <- IO.fromEither(providerEither).adaptError { case ex =>
              new RuntimeException(s"Invalid Token: ${ex.getMessage}")
            }

            providerConfEither <- ProviderDetailsRepository.getProviderConf(provider.providerId)
            _ <- IO(println(s"Provider Configuration: $providerConfEither"))
            providerConf <- IO.fromEither(providerConfEither).adaptError { case ex =>
              new RuntimeException(s"Provider configuration not found: ${ex.getMessage}")
            }
          } yield Map(
            "max_ram" -> providerConf.providerAllowedRam,
            "max_cpu" -> providerConf.providerAllowedVcpu,
            "max_disk" -> providerConf.providerAllowedStorage,
            "max_vms" -> providerConf.providerAllowedVms,
            "max_networks" -> providerConf.providerAllowedNetworks
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
      uiLoginRequired { user =>
        entity(as[UpdateConfigRequest]) { request =>
          val providerId = request.providerId
          val providerAllowedRam = request.providerAllowedRam
          val providerAllowedVcpu = request.providerAllowedVcpu
          val providerAllowedStorage = request.providerAllowedStorage
          val providerAllowedVms = request.providerAllowedVms
          val providerAllowedNetworks = request.providerAllowedNetworks

          if (
            providerId.isEmpty ||
            providerAllowedRam <= 0 ||
            providerAllowedVcpu <= 0 ||
            providerAllowedStorage <= 0 ||
            providerAllowedVms <= 0 ||
            providerAllowedNetworks <= 0
          ) {
            complete((400, Map("error" -> "Missing or invalid required fields").asJson))
          } else {
            val result = for {
              providerEither <- ProviderDetailsRepository.getFullProviderDetails(providerId)
              fullProviderDetails <- IO.fromEither(providerEither).adaptError { case ex =>
                new RuntimeException(s"Provider not found: ${ex.getMessage}")
              }

              // Validate used specs against allowed specs
              _ <- IO.raiseWhen(fullProviderDetails.providerUsedRam.exists(_ > providerAllowedRam))(
                new RuntimeException("More RAM already being used by clients than allowed.")
              )
              _ <- IO.raiseWhen(fullProviderDetails.providerUsedVcpu.exists(_ > providerAllowedVcpu))(
                new RuntimeException("More VCPU already being used by clients than allowed.")
              )
              _ <- IO.raiseWhen(fullProviderDetails.providerUsedStorage.exists(_ > providerAllowedStorage))(
                new RuntimeException("More Storage already being used by clients than allowed.")
              )
              _ <- IO.raiseWhen(fullProviderDetails.providerUsedNetworks.exists(_ > providerAllowedNetworks))(
                new RuntimeException("More Networks already being used by clients than allowed.")
              )
              _ <- IO.raiseWhen(fullProviderDetails.providerUsedVms.exists(_ > providerAllowedVms))(
                new RuntimeException("More VMs already being used by clients than allowed.")
              )

              // Send request to provider
              response <- ProviderService.sendUpdateRequestToProvider(
                fullProviderDetails.providerUrl,
                fullProviderDetails.managementServerVerificationToken.getOrElse(""),
                providerAllowedRam,
                providerAllowedVcpu,
                providerAllowedStorage,
                providerAllowedVms,
                providerAllowedNetworks
              )
              _ <- response match {
                case Left(errorMessage) =>
                  IO.raiseError(new RuntimeException(s"Failed to update provider configuration: $errorMessage"))
                case Right(_) =>
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
}
