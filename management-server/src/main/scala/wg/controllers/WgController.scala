package wg.controllers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

import wg.WgDetailsRepository
import wg.WgServices
import vms.VmDetailsRepository
import providers.ProviderDetailsRepository

object WgController {

  def connectWireguard: Route = path("connect") {
    post {
      entity(as[Map[String, String]]) { request =>
        val userId = request.get("user_id")
        val cliId = request.get("cli_id")
        val vmName = request.get("vm_name")
        val interfaceName = request.getOrElse("interface_name", "wg0")

        if (userId.isEmpty || cliId.isEmpty || vmName.isEmpty) {
          complete((400, Map("error" -> "Missing required fields: user_id, cli_id, vm_name").asJson))
        } else {
          val result = for {
            // Fetch VM details
            vmDetails <- VmDetailsRepository.getVmDetailsByName(vmName.get, userId.get)
            vmDetails<- IO.fromEither(vmDetails)
            vmId = vmDetails._1
            providerId = vmDetails._2
            internalVmName = vmDetails._3

            // Fetch provider details
            providerDetails <- ProviderDetailsRepository.getProviderDetails(providerId)
            providerDetails <- IO.fromEither(providerDetails.left.map(new Exception(_)))
            providerUrl = providerDetails.providerUrl
            verificationToken = providerDetails.verificationToken

            // Generate Wireguard keys for client
            clientKeys <- WgServices.generateWireguardKeypair

            // Add client peer to the network server
            clientAddPeerResponse <- WgServices.addPeerToNetworkServer(
              interfaceName,
              s"${cliId.get}->${vmId}",
              clientKeys._1
            )
            clientInterfaceDetails <- IO.fromEither(clientAddPeerResponse.left.map(new Exception(_)))

            // Generate Wireguard keys for VM
            vmKeys <- WgServices.generateWireguardKeypair

            // Add VM peer to the network server
            vmAddPeerResponse <- WgServices.addPeerToNetworkServer(
              interfaceName,
              s"${vmId}->${cliId.get}",
              vmKeys._1
            )
            vmInterfaceDetails <- IO.fromEither(vmAddPeerResponse.left.map(new Exception(_)))

            // Combine interface details
            combinedInterfaceDetails = Map(
              "interface_allowed_ips" -> clientInterfaceDetails.getOrElse("interface_allowed_ips", ""),
              "interface_endpoint" -> clientInterfaceDetails.getOrElse("interface_endpoint", ""),
              "interface_name" -> clientInterfaceDetails.getOrElse("interface_name", ""),
              "interface_public_key" -> clientInterfaceDetails.getOrElse("interface_public_key", ""),
              "client_peer_name" -> clientInterfaceDetails.getOrElse("peer_name", ""),
              "client_peer_address" -> clientInterfaceDetails.getOrElse("peer_address", ""),
              "client_peer_public_key" -> clientInterfaceDetails.getOrElse("peer_public_key", ""),
              "client_peer_private_key" -> clientKeys._2,
              "vm_peer_name" -> vmInterfaceDetails.getOrElse("peer_name", ""),
              "vm_peer_address" -> vmInterfaceDetails.getOrElse("peer_address", ""),
              "vm_peer_public_key" -> vmInterfaceDetails.getOrElse("peer_public_key", ""),
              "vm_peer_private_key" -> vmKeys._2
            )

            // Setup Wireguard on the VM
            setupResponse <- WgServices.setupWireguardOnVM(
              providerUrl,
              internalVmName,
              verificationToken,
              cliId.get,
              combinedInterfaceDetails
            )
            _ <- IO.fromEither(setupResponse.left.map(new Exception(_)))

            // Update Wireguard details in the database
            _ <- WgDetailsRepository.updateWireguardDetails(internalVmName, cliId.get, combinedInterfaceDetails)
          } yield combinedInterfaceDetails

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
}