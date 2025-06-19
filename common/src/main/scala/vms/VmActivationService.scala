package vms

import provider.ProviderService
import java.time.LocalDateTime
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.either._

import vms._
import provider._

object VmActivationService {

  def activateVm(providerId: String, vmId: String, userId: String): IO[Either[String, String]] = {
    for {
      providerResult <- ProviderService.getProviderDetails(providerId) // Returns IO[Either[String, ProviderDetails]]
      internalVmNameResult <- VmDetailsRepository.getInternalVmName(vmId, userId) // Returns IO[Either[String, String]]
      internalVmName <- IO.fromEither(internalVmNameResult) // Unwraps the Either
      provider <- IO.fromEither(providerResult.leftMap(new RuntimeException(_))) // Unwraps the Either to get the provider object
      _ <- IO.fromEither(
        NetworkService.ensureDefaultNetworkActive(
          provider.providerUrl,
          provider.verificationToken
        ).left.map(new RuntimeException(_))
      )
      response <- ProviderService.activateVm(provider.providerUrl, internalVmName, provider.verificationToken) // Returns IO[Either[String, String]]
    } yield response
  }

  def deactivateVm(providerId: String, vmId: String, userId: String): IO[Either[String, String]] = {
    for {
      providerResult <- ProviderService.getProviderDetails(providerId) // Returns IO[Either[String, ProviderDetails]]
      provider <- IO.fromEither(providerResult.leftMap(new RuntimeException(_))) // Unwraps the Either
      internalVmNameResult <- VmDetailsRepository.getInternalVmName(vmId, userId) // Returns IO[Either[String, String]]
      internalVmName <- IO.fromEither(internalVmNameResult) // Unwraps the Either
      response <- ProviderService.deactivateVm(provider.providerUrl, internalVmName, provider.verificationToken) // Returns IO[Either[String, String]]
    } yield response
  }

  def deleteVm(providerId: String, vmId: String, userId: String): IO[Either[String, String]] = {
    for {
      providerResult <- ProviderService.getProviderDetails(providerId) // Returns IO[Either[String, ProviderDetails]]
      provider <- IO.fromEither(providerResult.leftMap(new RuntimeException(_))) // Unwraps the Either
      internalVmNameResult <- VmDetailsRepository.getInternalVmName(vmId, userId) // Returns IO[Either[String, String]]
      internalVmName <- IO.fromEither(internalVmNameResult) // Unwraps the Either
      validateResult <- VmStatusRepository.validateVmId(vmId, userId) // Returns IO[Either[String, Unit]]
      _ <- IO.fromEither(validateResult) // Ensures the VM ID is valid
      response <- ProviderService.deleteVm(provider.providerUrl, internalVmName, provider.verificationToken) // Returns IO[Either[String, String]]
      markDeletedResult <- VmStatusRepository.markVmAsDeleted(vmId) // Returns IO[Either[String, Unit]]
      _ <- IO.fromEither(markDeletedResult) // Marks the VM as deleted
    } yield response
  }
}

