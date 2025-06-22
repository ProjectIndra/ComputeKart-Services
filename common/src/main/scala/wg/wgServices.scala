package wg

import cats.effect.IO
import io.circe.syntax._
import io.circe.Json
import scalaj.http.Http
import java.util.Base64
import java.security.SecureRandom
import com.typesafe.config.ConfigFactory

object WgServices {

  val config = ConfigFactory.load()
  val networkServer = config.getString("servers.networkserver")

  def generateWireguardKeypair: IO[(String, String)] = IO {
    val random = new SecureRandom()
    val privateKey = new Array[Byte](32)
    random.nextBytes(privateKey)

    val publicKey = Base64.getEncoder.encodeToString(privateKey)
    val privateKeyB64 = Base64.getEncoder.encodeToString(privateKey)

    (publicKey, privateKeyB64)
  }

  def addPeerToNetworkServer(interfaceName: String, peerName: String, publicKey: String): IO[Either[String, Map[String, String]]] = IO {

    val response = Http(s"$networkServer/api/addPeer")
      .postData(
        s"""
        {
          "interface_name": "$interfaceName",
          "peer_name": "$peerName",
          "public_key": "$publicKey"
        }
        """
      )
      .header("Content-Type", "application/json")
      .asString

    if (response.is2xx) {
      Right(io.circe.parser.parse(response.body).getOrElse(Json.obj()).as[Map[String, String]].getOrElse(Map.empty))
    } else {
      Left(s"Failed to add peer to network server: ${response.body}")
    }
  }

  def setupWireguardOnVM(
    providerUrl: String,
    internalVmName: String,
    verificationToken: String,
    cliId: String,
    combinedInterfaceDetails: Map[String, Any]
  ): IO[Either[String, Unit]] = IO {
    val response = Http(s"$providerUrl/vm/ssh/setup_wireguard")
      .postData(
        s"""
        {
          "combined_interface_details": ${io.circe.syntax.EncoderOps(combinedInterfaceDetails.view.mapValues(_.toString).toMap).asJson.noSpaces}
        }
        """
      )
      .header("Content-Type", "application/json")
      .header("authorization", verificationToken)
      .asString

    if (response.is2xx) {
      Right(())
    } else {
      Left(s"Failed to setup Wireguard on VM: ${response.body}")
    }
  }
}
