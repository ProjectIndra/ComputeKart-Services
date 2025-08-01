package providers

import cats.effect.IO
import doobie.implicits._
import main.SqlDB

case class FullProviderDetails(
  providerId: String,
  providerName: String,
  providerStatus: String,
  providerRamCapacity: Int,
  providerVcpuCapacity: Int,
  providerStorageCapacity: Int,
  providerUrl: String,
  managementServerVerificationToken: Option[String],
  providerAllowedRam: Int,
  providerAllowedVcpu: Int,
  providerAllowedStorage: Int,
  providerAllowedVms: Int,
  providerAllowedNetworks: Int,
  providerUsedRam: Option[Int],
  providerUsedVcpu: Option[Int],
  providerUsedStorage: Option[Int],
  providerUsedVms: Option[Int],
  providerUsedNetworks: Option[Int]
)

case class ProviderDetails(
  providerId: String,
  providerUrl: String,
  verificationToken: String,
  providerName: String,
  providerUserId: String,
  providerStatus: String
)

case class ProviderConf(
  providerAllowedRam: Int,
  providerAllowedVcpu: Int,
  providerAllowedStorage: Int,
  providerAllowedVms: Int,
  providerAllowedNetworks: Int
)

object ProviderDetailsRepository {

  def getProvidersList(userId: String): IO[Either[Throwable, List[ProviderDetails]]] = {
    val query =
      sql"""
      SELECT provider_id, provider_url, management_server_verification_token, provider_name, user_id, provider_status
      FROM provider_details
      WHERE user_id = $userId
    """.query[ProviderDetails].to[List]

    SqlDB.runQuery(query, "Get Providers List")
  }

  def getProvidersListFiltered(userId: String, providerName: String): IO[Either[Throwable, List[ProviderDetails]]] = {
    val query =
      sql"""
      SELECT provider_id, provider_url, management_server_verification_token, provider_name, user_id, provider_status
      FROM provider_details
      WHERE user_id = $userId AND provider_name LIKE %$providerName%
    """.query[ProviderDetails].to[List]

    SqlDB.runQuery(query, "Get Providers List Filtered")
  }

  def canCreateVm(
    providerId: String,
    vcpus: Option[Int],
    ram: Option[Int],
    storage: Option[Int]
  ): IO[Either[String, Boolean]] = {
    for {
      providerDetails <- fetchProviderDetails(providerId)
      provider <- IO.fromEither(providerDetails).adaptError { case _ =>
        new RuntimeException("Provider not found")
      }
      _ <- IO.raiseWhen(provider.providerStatus != "active")(
        new RuntimeException("Provider is not active")
      )

      // Call the provider's endpoint to check VM creation eligibility
      canCreate <- ProviderService.queryVmCreation(
        provider.providerUrl,
        provider.verificationToken,
        vcpus,
        ram,
        storage
      )
    } yield canCreate
  }.attempt.map {
    case Right(result) => result
    case Left(e) =>
      println(s"Error checking VM creation eligibility: ${e.getMessage}")
      Left(e.getMessage)
  }

  def getProvidersByUserId(userId: String): IO[Either[Throwable, List[ProviderDetails]]] = {
    val query =
      sql"""
      SELECT provider_id, provider_name, user_id, provider_type, provider_rating
      FROM provider_details
      WHERE user_id = $userId
    """.query[ProviderDetails].to[List]

    SqlDB.runQuery(query, "Get Providers By User ID")
  }

  def getProviderConf(providerId: String): IO[Either[Throwable, ProviderConf]] = {
    val query =
      sql"""
      SELECT provider_id, provider_allowed_ram, provider_allowed_vcpu, provider_allowed_storage, provider_allowed_vms, provider_allowed_networks
      FROM provider_conf
      WHERE provider_id = $providerId
    """.query[ProviderConf].option

    SqlDB.runQueryEither(query, "Get Provider Configuration")
  }

  def getProviderByToken(managementServerVerificationToken: String): IO[Either[Throwable, ProviderDetails]] = {
    val query =
      sql"""
      SELECT provider_id, provider_name, provider_status, provider_ram_capacity, provider_vcpu_capacity, provider_storage_capacity, provider_url, management_server_verification_token
      FROM provider_details
      WHERE management_server_verification_token = $managementServerVerificationToken
    """.query[ProviderDetails].option

    SqlDB.runQueryEither(query, "Get Provider By Token")
  }

  def updateProviderConf(
    providerId: String,
    providerAllowedRam: Int,
    providerAllowedVcpu: Int,
    providerAllowedStorage: Int,
    providerAllowedVms: Int,
    providerAllowedNetworks: Int
  ): IO[Either[Throwable, Unit]] = {
    val query =
      sql"""
        UPDATE provider_conf
        SET 
          provider_allowed_ram = $providerAllowedRam,
          provider_allowed_vcpu = $providerAllowedVcpu,
          provider_allowed_storage = $providerAllowedStorage,
          provider_allowed_vms = $providerAllowedVms,
          provider_allowed_networks = $providerAllowedNetworks
        WHERE provider_id = $providerId
      """.update.run

    SqlDB.runUpdateQuery(query, "Update Provider Configuration")
  }

  def updateProviderDetails(
    providerId: String,
    providerRamCapacity: Int,
    providerVcpuCapacity: Int,
    providerStorageCapacity: Int,
    providerUrl: String,
    providerStatus: String,
    managementServerVerificationToken: String
  ): IO[Either[Throwable, Unit]] = {
    val query =
      sql"""
        UPDATE provider_details
        SET 
          provider_ram_capacity = $providerRamCapacity,
          provider_vcpu_capacity = $providerVcpuCapacity,
          provider_storage_capacity = $providerStorageCapacity,
          provider_url = $providerUrl,
          provider_status = $providerStatus,
          management_server_verification_token = $managementServerVerificationToken
        WHERE provider_id = $providerId
      """.update.run

    SqlDB.runUpdateQuery(query, "Update Provider Details")
  }

  def insertProviderDetails(
    providerId: String,
    providerName: String,
    providerStatus: String,
    providerRamCapacity: Int,
    providerVcpuCapacity: Int,
    providerStorageCapacity: Int,
    providerUrl: String,
    managementServerVerificationToken: String
  ): IO[Either[Throwable, Unit]] = {
    val query =
      sql"""
        INSERT INTO provider_details (
          provider_id,
          provider_name,
          provider_status,
          provider_ram_capacity,
          provider_vcpu_capacity,
          provider_storage_capacity,
          provider_url,
          management_server_verification_token
        )
        VALUES (
          $providerId,
          $providerName,
          $providerStatus,
          $providerRamCapacity,
          $providerVcpuCapacity,
          $providerStorageCapacity,
          $providerUrl,
          $managementServerVerificationToken
        )
      """.update.run

    SqlDB.runUpdateQuery(query, "Insert Provider Details")
  }

  def insertProviderConf(
    providerId: String,
    providerAllowedRam: Int,
    providerAllowedVcpu: Int,
    providerAllowedStorage: Int,
    providerAllowedVms: Int,
    providerAllowedNetworks: Int
  ): IO[Either[Throwable, Unit]] = {
    val query =
      sql"""
        INSERT INTO provider_conf (
          provider_id,
          provider_allowed_ram,
          provider_allowed_vcpu,
          provider_allowed_storage,
          provider_allowed_vms,
          provider_allowed_networks
        )
        VALUES (
          $providerId,
          $providerAllowedRam,
          $providerAllowedVcpu,
          $providerAllowedStorage,
          $providerAllowedVms,
          $providerAllowedNetworks
        )
      """.update.run

    SqlDB.runUpdateQuery(query, "Insert Provider Configuration")
  }

  def fetchProviderDetails(providerId: String): IO[Either[Throwable, ProviderDetails]] = {
    val query =
      sql"""
          SELECT provider_id, provider_url, management_server_verification_token, provider_name, user_id, provider_status
          FROM provider_details
          WHERE provider_id = $providerId
          LIMIT 1
      """.query[ProviderDetails].option

    SqlDB.runQueryEither(query, "Fetch Provider Details")
  }

  def getFullProviderDetails(providerId: String): IO[Either[Throwable, FullProviderDetails]] = {
    val query =
      sql"""
        SELECT
          pd.provider_id,
          pd.provider_name,
          pd.provider_status,
          pd.provider_ram_capacity,
          pd.provider_vcpu_capacity,
          pd.provider_storage_capacity,
          pd.provider_url,
          pd.management_server_verification_token,
          pc.provider_allowed_ram,
          pc.provider_allowed_vcpu,
          pc.provider_allowed_storage,
          pc.provider_allowed_vms,
          pc.provider_allowed_networks,
          ps.provider_used_ram,
          ps.provider_used_vcpu,
          ps.provider_used_storage,
          ps.provider_used_vms,
          ps.provider_used_networks
        FROM provider_details pd
        LEFT JOIN provider_conf pc ON pd.provider_id = pc.provider_id
        LEFT JOIN provider_stats ps ON pd.provider_id = ps.provider_id
        WHERE pd.provider_id = $providerId
      """.query[FullProviderDetails].option

    SqlDB.runQueryEither(query, "Get Full Provider Details")
  }
}
