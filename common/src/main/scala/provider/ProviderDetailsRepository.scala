package provider

import cats.effect.IO
import doobie.implicits._
import doobie.util.transactor.Transactor
import main.SqlDB

import vms.VmDetailsRepository
import users.UserDetailsRepository

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

  def fetchProviderDetails(providerId: String): IO[Option[ProviderDetails]] = {
    val query =
      sql"""
          SELECT provider_id, provider_url, management_server_verification_token, provider_name, user_id, provider_status
          FROM provider
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
          providers.map {
            case (id, providerUrl, verificationToken, providerName, providerUserId, providerStatus) =>
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
          providers.map {
            case (id, providerUrl, verificationToken, providerName, providerUserId, providerStatus) =>
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
          clientDetails <- if (isActive) {
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

