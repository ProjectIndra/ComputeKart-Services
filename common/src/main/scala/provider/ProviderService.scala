package provider

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import doobie.implicits._
import doobie.util.transactor.Transactor
import io.circe.generic.auto._
import io.circe.syntax._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import doobie.implicits._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}
import scalaj.http.HttpResponse
import scalaj.http._

import main.SqlDB

case class ProviderDetails(
    providerUrl: String,
    verificationToken: String,
    providerName: String,
    providerUserId: String,
    providerStatus: String
)

import scala.concurrent.ExecutionContext
import io.circe.{Encoder, Json}

object ProviderService {
  
  implicit val ec: ExecutionContext = ExecutionContext.global
  // Custom encoder for Map[String, Any]
  implicit val mapAnyEncoder: Encoder[Map[String, Any]] = Encoder.instance { map =>
    Json.obj(
      map.map {
        case (key, value: String)  => key -> Json.fromString(value)
        case (key, value: Int)     => key -> Json.fromInt(value)
        case (key, value: Long)    => key -> Json.fromLong(value)
        case (key, value: Double)  => key -> Json.fromDoubleOrNull(value)
        case (key, value: Boolean) => key -> Json.fromBoolean(value)
        case (key, value: Map[_, _]) =>
          key -> mapAnyEncoder.apply(value.asInstanceOf[Map[String, Any]])
        case (key, _) => key -> Json.Null
      }.toSeq: _*
    )
  }

  def createVmOnProvider(
      providerUrl: String,
      vmData: Map[String, Any],
      verificationToken: String
  ): Either[String, Map[String, Any]] = {
    val headers = Map("authorization" -> verificationToken)

    val response = Try(
      Http(s"$providerUrl/vm/create_qvm")
        .postData(io.circe.syntax.EncoderOps(vmData).asJson.noSpaces)
        .headers(headers)
        .asString
    )

    response match {
      case Success(res: HttpResponse[String]) if res.is2xx =>
        io.circe.parser.decode[Map[String, io.circe.Json]](res.body) match {
          case Right(decodedJson) => Right(decodedJson.view.mapValues(_.noSpaces).toMap)
          case Left(error) => Left(s"Failed to decode response: ${error.getMessage}")
        }
      case Success(res: HttpResponse[String]) =>
        Left(s"Failed to create VM on provider: ${res.body}")
      case Failure(exception) =>
        Left(s"Error while creating VM on provider: ${exception.getMessage}")
    }
  }

  def fetchProviderDetails(providerId: String): Option[ProviderDetails] = {
    
    val query =
        sql"""
            SELECT provider_url, management_server_verification_token, provider_name, user_id, provider_status
            FROM provider
            WHERE _id = $providerId
            LIMIT 1
        """.query[(String, String, String, String)].option

    val result = SqlDB.transactor.use(xa => query.transact(xa)).unsafeToFuture()

    Await.result(
      result.map {
        case Some((providerUrl, verificationToken, providerName, providerUserId)) =>
          Some(ProviderDetails(providerUrl, verificationToken, providerName, providerUserId, "active"))
        case None => None
      }.recover {
        case ex: Exception =>
          println(s"Error fetching provider details: ${ex.getMessage}")
          None
      },
      10.seconds
    )
  }

  def activateVm(providerUrl: String, internalVmName: String, verificationToken: String): IO[Either[String, String]] = IO {
    val headers = Map("authorization" -> verificationToken)
    val response = Try(Http(s"$providerUrl/vm/activate").postData(s"""{"name": "$internalVmName"}""").headers(headers).asString)

    response match {
      case Success(res) if res.is2xx => Right("VM activated successfully")
      case Success(res) => Left(s"Failed to activate VM: ${res.body}")
      case Failure(exception) => Left(s"Error while activating VM: ${exception.getMessage}")
    }
  }

  def deactivateVm(providerUrl: String, internalVmName: String, verificationToken: String): IO[Either[String, String]] = IO {
    val headers = Map("authorization" -> verificationToken)
    val response = Try(Http(s"$providerUrl/vm/deactivate").postData(s"""{"name": "$internalVmName"}""").headers(headers).asString)

    response match {
      case Success(res) if res.is2xx => Right("VM deactivated successfully")
      case Success(res) => Left(s"Failed to deactivate VM: ${res.body}")
      case Failure(exception) => Left(s"Error while deactivating VM: ${exception.getMessage}")
    }
  }

  def deleteVm(providerUrl: String, internalVmName: String, verificationToken: String): IO[Either[String, String]] = IO {
    val headers = Map("authorization" -> verificationToken)
    val response = Try(Http(s"$providerUrl/vm/delete").postData(s"""{"name": "$internalVmName"}""").headers(headers).asString)

    response match {
      case Success(res) if res.is2xx => Right("VM deleted successfully")
      case Success(res) => Left(s"Failed to delete VM: ${res.body}")
      case Failure(exception) => Left(s"Error while deleting VM: ${exception.getMessage}")
    }
  }

  def getProviderUrlAndToken(providerId: String): IO[Either[String, (String, String)]] = {
    IO {
      fetchProviderDetails(providerId) match {
        case Some(providerDetails) =>
          Right((providerDetails.providerUrl, providerDetails.verificationToken))
        case None =>
          Left(s"Provider with ID $providerId not found")
      }
    }
  }

  def getProviderDetails(providerId: String): IO[Either[String, ProviderDetails]] = {
    IO {
      fetchProviderDetails(providerId) match {
        case Some(providerDetails) =>
          Right(providerDetails)
        case None =>
          Left(s"Provider with ID $providerId not found")
      }
    }
  }
}

