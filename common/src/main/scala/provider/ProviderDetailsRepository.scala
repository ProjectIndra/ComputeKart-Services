package providers

import cats.effect.IO
import doobie.implicits._
import doobie.util.transactor.Transactor
import main.SqlDB

import vms.VmDetailsRepository
import users.UserDetailsRepository

case class FullProviderDetails(
  userId: String,
  providerId: String,
  providerName: String,
  providerStatus: String,
  providerRamCapacity: String,
  providerVcpuCapacity: String,
  providerStorageCapacity: String,
  providerUsedRam: Option[String],
  providerUsedVcpu: Option[String],
  providerUsedStorage: Option[String],
  providerUsedVms: Option[String],
  providerUsedNetworks: Option[String],
  providerRating: Float,
  providerUrl: String,
  managementServerVerificationToken: Option[String]
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

  def getProvidersList(userId: String): IO[List[ProviderDetails]] = {
    val query =
      sql"""
      SELECT provider_id, provider_url, management_server_verification_token, provider_name, user_id, provider_status
      FROM provider_details
      WHERE user_id = $userId
    """.query[ProviderDetails].to[List]

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(providers) => providers
        case Left(e) =>
          println(s"Error fetching providers list: ${e.getMessage}")
          List.empty[ProviderDetails]
      }
    }
  }

  def canCreateVm(
    providerId: String,
    vcpus: Option[Int],
    ram: Option[Int],
    storage: Option[Int]
  ): IO[Either[String, Boolean]] = {
    for {
      providerDetails <- fetchProviderDetails(providerId)
      provider <- IO.fromOption(providerDetails)(new RuntimeException("Provider not found"))
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

  def getProvidersByUserId(userId: String): IO[List[ProviderDetails]] = {
    val query =
      sql"""
      SELECT provider_id, provider_name, user_id, provider_type, provider_rating
      FROM provider_details
      WHERE user_id = $userId
    """.query[ProviderDetails].to[List]

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(providers) => providers
        case Left(e) =>
          println(s"Error fetching providers by user ID: ${e.getMessage}")
          List.empty[ProviderDetails]
      }
    }
  }

  def getProviderConf(providerId: String): IO[Option[ProviderConf]] = {
    val query =
      sql"""
      SELECT provider_id, provider_allowed_ram, provider_allowed_vcpu, provider_allowed_storage, provider_allowed_vms, provider_allowed_networks
      FROM provider_conf
      WHERE provider_id = $providerId
    """.query[ProviderConf].option

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(conf) => conf
        case Left(e) =>
          println(s"Error fetching provider configuration: ${e.getMessage}")
          None
      }
    }
  }

  def getProviderByToken(managementServerVerificationToken: String): IO[Option[ProviderDetails]] = {
    val query =
      sql"""
      SELECT provider_id, provider_name, provider_status, provider_ram_capacity, provider_vcpu_capacity, provider_storage_capacity, provider_url, management_server_verification_token
      FROM provider_details
      WHERE management_server_verification_token = $managementServerVerificationToken
    """.query[ProviderDetails].option

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(provider) => provider
        case Left(e) =>
          println(s"Error fetching provider by token: ${e.getMessage}")
          None
      }
    }
  }

  def updateProviderConf(
    providerId: String,
    providerAllowedRam: Int,
    providerAllowedVcpu: Int,
    providerAllowedStorage: Int,
    providerAllowedVms: Int,
    providerAllowedNetworks: Int
  ): IO[Either[String, Unit]] = {
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

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(_) => Right(())
        case Left(e) =>
          println(s"Error updating provider configuration: ${e.getMessage}")
          Left(s"Error updating provider configuration: ${e.getMessage}")
      }
    }
  }

  def updateProviderDetails(
    providerId: String,
    providerRamCapacity: Int,
    providerVcpuCapacity: Int,
    providerStorageCapacity: Int,
    providerUrl: String,
    providerStatus: String,
    managementServerVerificationToken: String
  ): IO[Either[String, Unit]] = {
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

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(_) => Right(())
        case Left(e) =>
          println(s"Error updating provider details: ${e.getMessage}")
          Left(s"Error updating provider details: ${e.getMessage}")
      }
    }
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
  ): IO[Either[String, Unit]] = {
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

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(_) => Right(())
        case Left(e) =>
          println(s"Error inserting provider details: ${e.getMessage}")
          Left(s"Error inserting provider details: ${e.getMessage}")
      }
    }
  }
  def insertProviderConf(
    providerId: String,
    providerAllowedRam: Int,
    providerAllowedVcpu: Int,
    providerAllowedStorage: Int,
    providerAllowedVms: Int,
    providerAllowedNetworks: Int
  ): IO[Either[String, Unit]] = {
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

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(_) => Right(())
        case Left(e) =>
          println(s"Error inserting provider configuration: ${e.getMessage}")
          Left(s"Error inserting provider configuration: ${e.getMessage}")
      }
    }
  }

  def fetchProviderDetails(providerId: String): IO[Option[ProviderDetails]] = {
    val query =
      sql"""
          SELECT provider_id, provider_url, management_server_verification_token, provider_name, user_id, provider_status
          FROM providers
          WHERE provider_id = $providerId
          LIMIT 1
      """.query[(String, String, String, String, String, String)].option

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(Some((id, providerUrl, verificationToken, providerName, providerUserId, providerStatus))) =>
          Some(ProviderDetails(id, providerUrl, verificationToken, providerName, providerUserId, providerStatus))
        case Right(None) => None
        case Left(e) =>
          println(s"Error fetching provider details: ${e.getMessage}")
          None
      }
    }
  }

  def fetchFullProviderDetails(providerId: String): IO[Option[FullProviderDetails]] = {
    val query =
      sql"""
          SELECT user_id, provider_id, provider_name, provider_status, provider_ram_capacity, provider_vcpu_capacity,
                 provider_storage_capacity, provider_used_ram, provider_used_vcpu, provider_used_storage, provider_used_vms,
                 provider_used_networks, provider_rating, provider_url, management_server_verification_token
          FROM provider
          WHERE provider_id = $providerId
          LIMIT 1
      """
        .query[
          (
            String,
            String,
            String,
            String,
            String,
            String,
            String,
            Option[String],
            Option[String],
            Option[String],
            Option[String],
            Option[String],
            Float,
            String,
            Option[String]
          )
        ]
        .option

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(
              Some(
                (
                  userId,
                  id,
                  name,
                  status,
                  ramCapacity,
                  vcpuCapacity,
                  storageCapacity,
                  usedRam,
                  usedVcpu,
                  usedStorage,
                  usedVms,
                  usedNetworks,
                  rating,
                  url,
                  verificationToken
                )
              )
            ) =>
          Some(
            FullProviderDetails(
              userId,
              id,
              name,
              status,
              ramCapacity,
              vcpuCapacity,
              storageCapacity,
              usedRam,
              usedVcpu,
              usedStorage,
              usedVms,
              usedNetworks,
              rating,
              url,
              verificationToken
            )
          )
        case Right(None) => None
        case Left(e) =>
          println(s"Error fetching full provider details: ${e.getMessage}")
          None
      }
    }
  }

  def getProviderUrlAndToken(providerId: String): IO[Either[String, (String, String)]] = {
    fetchProviderDetails(providerId).map {
      case Some(providerDetails) =>
        Right((providerDetails.providerUrl, providerDetails.verificationToken))
      case None =>
        Left(s"Provider with ID $providerId not found")
    }
  }

  def getProviderDetails(providerId: String): IO[Either[String, ProviderDetails]] = {
    fetchProviderDetails(providerId).map {
      case Some(providerDetails) =>
        Right(providerDetails)
      case None =>
        Left(s"Provider with ID $providerId not found")
    }
  }

  def getActiveProviders: IO[List[ProviderDetails]] = {
    val query =
      sql"""
          SELECT provider_id, provider_url, management_server_verification_token, provider_name, user_id, provider_status
          FROM provider
          WHERE provider_status = 'active'
      """.query[(String, String, String, String, String, String)].to[List]

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(providers) =>
          providers.map { case (id, providerUrl, verificationToken, providerName, providerUserId, providerStatus) =>
            ProviderDetails(id, providerUrl, verificationToken, providerName, providerUserId, providerStatus)
          }
        case Left(e) =>
          println(s"Error fetching active providers: ${e.getMessage}")
          List.empty[ProviderDetails]
      }
    }
  }

  def getProviderDetailsForUser(userId: String): IO[List[ProviderDetails]] = {
    val query =
      sql"""
          SELECT provider_id, provider_url, management_server_verification_token, provider_name, user_id, provider_status
          FROM provider
          WHERE user_id = $userId
      """.query[(String, String, String, String, String, String)].to[List]

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(providers) =>
          providers.map { case (id, providerUrl, verificationToken, providerName, providerUserId, providerStatus) =>
            ProviderDetails(id, providerUrl, verificationToken, providerName, providerUserId, providerStatus)
          }
        case Left(e) =>
          println(s"Error fetching provider details for user $userId: ${e.getMessage}")
          List.empty[ProviderDetails]
      }
    }
  }

  def getProviderConfDetails(providerId: String): IO[Option[ProviderConf]] = {
    val query =
      sql"""
              SELECT provider_allowed_ram, provider_allowed_vcpu, provider_allowed_storage, provider_allowed_vms, provider_allowed_networks
              FROM provider_conf
              WHERE provider_id = $providerId
      """.query[(Int, Int, Int, Int, Int)].option

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(Some((allowedRam, allowedVcpu, allowedStorage, allowedVms, allowedNetworks))) =>
          Some(
            ProviderConf(
              providerAllowedRam = allowedRam,
              providerAllowedVcpu = allowedVcpu,
              providerAllowedStorage = allowedStorage,
              providerAllowedVms = allowedVms,
              providerAllowedNetworks = allowedNetworks
            )
          )
        case Right(None) =>
          None
        case Left(e) =>
          println(s"Error fetching provider configuration details: ${e.getMessage}")
          None
      }
    }
  }

  def getProviderClientsDetails(providerUserId: String): IO[Either[String, List[Map[String, Any]]]] = {
    for {
      vmClientsResult <- VmDetailsRepository.getVmClients(providerUserId)
      vmClients <- IO.fromEither(vmClientsResult.left.map(e => new RuntimeException(s"Error fetching VM clients: ${e.getMessage}")))
      clientDetails <- vmClients.foldLeft(IO.pure(List.empty[Map[String, Any]])) { (accIO, vmClient) =>
        val (clientUserId, vmId) = vmClient

        for {
          acc <- accIO
          isActiveResult <- VmDetailsRepository.isVmActive(vmId, clientUserId)
          isActive <- IO.fromEither(isActiveResult.left.map(e => new RuntimeException(s"Error checking VM active status: ${e.getMessage}")))
          clientDetails <-
            if (isActive) {
              UserDetailsRepository.getClientDetails(clientUserId).map {
                case Right(Some(details)) => Some(details)
                case _ => None
              }
            } else IO.pure(None)
        } yield {
          clientDetails match {
            case Some(details) if !acc.exists(_.get("user_id") == Some(clientUserId)) =>
              acc :+ Map(
                "user_id" -> details.userId,
                "name" -> (details.firstName + " " + details.lastName),
                "email" -> details.email
              )
            case _ => acc
          }
        }
      }
    } yield Right(clientDetails)
  }.attempt.map {
    case Right(result) => result
    case Left(e: Throwable) =>
      println(s"Error fetching provider client details: ${e.getMessage}")
      Left(s"Error fetching provider client details: ${e.getMessage}")
  }
}
