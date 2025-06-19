package vms

import provider.{ProviderService, NetworkService}
import java.util.UUID
import java.time.LocalDateTime
import cats.effect.IO
import cats.effect.unsafe.implicits.global

import vms.VmDetails
import vms.VmStatus
import provider.ProviderService.createVmOnProvider
import provider.ProviderDetails

object VmCreationService {

  def createVm(
      clientUserId: String,
      vcpus: Int = 2,
      ram: Int = 2048,
      storage: Int = 20,
      vmImageType: String = "ubuntu",
      providerId: String,
      vmName: Option[String] = None
  ): Either[String, String] = {
    val generatedVmName = vmName.getOrElse(s"vm-${UUID.randomUUID()}")
    val internalVmName = UUID.randomUUID().toString

    if (vcpus <= 0 && ram <= 0 && storage <= 0) {
      return Left("At least one of vcpu, ram, or storage is required")
    }
    if (providerId.isEmpty) {
      return Left("Provider selection is required")
    }

    VmDetailsRepository.isVmNameExists(clientUserId, generatedVmName).unsafeRunSync() match {
      case Right(true) => return Left("VM name already exists. You need to have a unique VM name.")
      case Right(false) => // Continue execution
      case Left(error) => return Left(s"Error checking VM name existence: ${error.getMessage}")
    }
    return Left("VM name already exists. You need to have a unique VM name.")

    createVmAfterInputVerification(
      clientUserId,
      vcpus,
      ram,
      storage,
      vmImageType,
      providerId,
      generatedVmName,
      internalVmName
    )

  }

  def createVmAfterInputVerification(
      clientUserId: String,
      vcpus: Int,
      ram: Int,
      storage: Int,
      vmImageType: String,
      providerId: String,
      generatedVmName: String,
      internalVmName: String
  ): Either[String, String] = {
    val providerResponse = ProviderService.fetchProviderDetails(providerId)
    providerResponse match {
      case None => return Left("Provider not found")
      case Some(provider) if provider.providerStatus != "active" =>
        return Left("Provider is not active")
      case Some(provider) =>
        createVmAfterProviderVerification(provider, clientUserId, vcpus, ram, storage, vmImageType, generatedVmName, internalVmName, providerId)
  }
}

def createVmAfterProviderVerification(
  provider:ProviderDetails,
  clientUserId: String,
  vcpus: Int,
  ram: Int,
  storage: Int,
  vmImageType: String,
  generatedVmName: String,
  internalVmName: String,
  providerId: String
): Either[String, String] = {
  val networkStatus = NetworkService.setupDefaultNetwork(provider.providerUrl, provider.verificationToken)
        if (networkStatus.isLeft) {
          return Left(networkStatus.swap.getOrElse("Unknown error"))
        }
  createVmAfterNetworkSetup(
    provider,
    clientUserId,
    vcpus,
    ram,
    storage,
    vmImageType,
    generatedVmName,
    internalVmName,
    providerId
  )
}

def createVmAfterNetworkSetup(
  provider: ProviderDetails,
  clientUserId: String,
  vcpus: Int,
  ram: Int,
  storage: Int,
  vmImageType: String,
  generatedVmName: String,
  internalVmName: String,
  providerId: String
): Either[String, String] = {
    val vmCreationResponse = createVmOnProvider(
      provider.providerUrl,
      Map(
        "vcpus" -> vcpus,
        "ram" -> ram,
        "storage" -> storage,
        "vmImageType" -> vmImageType,
        "vmName" -> generatedVmName
      ),
      provider.verificationToken
    )

    vmCreationResponse match {
      case Left(error) => Left(s"Failed to create VM on provider: $error")
      case Right(response) =>
        val vmDetails = VmDetails(
          clientUserId = clientUserId,
          vcpus = vcpus,
          ram = ram,
          storage = storage,
          vmImageType = vmImageType,
          vmName = generatedVmName,
          internalVmName = internalVmName,
          provider = provider,
          status = "active",
          createdAt = LocalDateTime.now(),
          providerId = providerId
        )

        VmDetailsRepository.insertVmDetails(vmDetails)

        VmStatusRepository.insertVmStatus(
            VmStatus(
            vmId = internalVmName,
            vmName = generatedVmName,
            status = "active",
            providerId = providerId,
            clientUserId = clientUserId,
            vmDeleted = false,
            vmDeletedAt = None,
            )
        )

        Right(generatedVmName)
    }
}
}

