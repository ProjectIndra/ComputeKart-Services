package providers

import scala.util.{Try, Success, Failure}
import io.circe.{Decoder, HCursor, Json}
import io.circe.generic.semiauto._
import scalaj.http._

object NetworkService {

  implicit val mapStringAnyDecoder: Decoder[Map[String, Any]] = (c: HCursor) => {
    c.value.asObject match {
      case Some(jsonObject) =>
        Right(
          jsonObject.toMap
            .map {
              case (key, json) if json.isString => key -> json.asString
              case (key, json) if json.isNumber => key -> json.asNumber.map(_.toDouble)
              case (key, json) if json.isBoolean => key -> json.asBoolean
              case (key, json) => key -> Some(json.noSpaces)
            }
            .collect { case (key, Some(value)) => key -> value }
        )
      case None => Left(io.circe.DecodingFailure("Expected JSON object", c.history))
    }
  }

  case class NetworkList(active_networks: List[String], inactive_networks: List[String])
  implicit val networkListDecoder: Decoder[NetworkList] = deriveDecoder

  def getNetworkList(providerUrl: String, verificationToken: String): Either[String, NetworkList] = {
    val headers = Map("authorization" -> verificationToken)
    val response = Try(Http(s"$providerUrl/network/list").headers(headers).asString)

    response match {
      case Success(res) if res.is2xx =>
        io.circe.parser.decode[NetworkList](res.body).left.map(_.getMessage)
      case Success(res) =>
        Left(s"Failed to fetch network list: ${res.body}")
      case Failure(exception) =>
        Left(s"Error while fetching network list: ${exception.getMessage}")
    }
  }

  def createDefaultNetwork(providerUrl: String, verificationToken: String): Either[String, Map[String, Any]] = {
    val headers = Map("authorization" -> verificationToken)
    val networkData = Map(
      "name" -> "default",
      "bridgeName" -> "virbr1",
      "forwardMode" -> "nat",
      "ipAddress" -> "192.168.122.1",
      "ipRangeStart" -> "192.168.122.100",
      "ipRangeEnd" -> "192.168.122.200",
      "netMask" -> "255.255.255.0"
    )

    val response = Try(Http(s"$providerUrl/network/create").postData(io.circe.syntax.EncoderOps(networkData).asJson.noSpaces).headers(headers).asString)

    response match {
      case Success(res) if res.is2xx =>
        Right(io.circe.parser.decode[Map[String, Any]](res.body).getOrElse(Map.empty))
      case Success(res) =>
        Left(s"Failed to create default network: ${res.body}")
      case Failure(exception) =>
        Left(s"Error while creating default network: ${exception.getMessage}")
    }
  }

  def activateDefaultNetwork(providerUrl: String, verificationToken: String): Either[String, Map[String, Any]] = {
    val headers = Map("authorization" -> verificationToken)
    val requestData = Map("name" -> "default")

    val response = Try(Http(s"$providerUrl/network/activate").postData(io.circe.syntax.EncoderOps(requestData).asJson.noSpaces).headers(headers).asString)

    response match {
      case Success(res) if res.is2xx =>
        Right(io.circe.parser.decode[Map[String, Any]](res.body).getOrElse(Map.empty))
      case Success(res) =>
        Left(s"Failed to activate default network: ${res.body}")
      case Failure(exception) =>
        Left(s"Error while activating default network: ${exception.getMessage}")
    }
  }

  def setupDefaultNetwork(providerUrl: String, verificationToken: String): Either[String, Unit] = {
    getNetworkList(providerUrl, verificationToken) match {
      case Right(networkList) =>
        val activeNetworks = networkList.active_networks
        val inactiveNetworks = networkList.inactive_networks

        if (!activeNetworks.contains("default") && !inactiveNetworks.contains("default")) {
          createDefaultNetwork(providerUrl, verificationToken) match {
            case Right(_) => Right(())
            case Left(error) => Left(error)
          }
        } else if (inactiveNetworks.contains("default")) {
          activateDefaultNetwork(providerUrl, verificationToken) match {
            case Right(_) => Right(())
            case Left(error) => Left(error)
          }
        } else {
          Right(())
        }

      case Left(error) => Left(error)
    }
  }

  def ensureDefaultNetworkActive(providerUrl: String, verificationToken: String): Either[String, Unit] = {
    setupDefaultNetwork(providerUrl, verificationToken) match {
      case Right(_) => Right(())
      case Left(error) => Left(s"Failed to ensure default network is active: $error")
    }
  }
}
