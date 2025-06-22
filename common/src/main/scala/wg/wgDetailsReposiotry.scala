package wg

import cats.effect.IO
import doobie.implicits._
import doobie.util.transactor.Transactor
import main.SqlDB
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.Json
import io.circe.syntax._

object WgDetailsRepository {

  def updateWireguardDetails(internalVmName: String, cliId: String, combinedInterfaceDetails: Map[String, Any]): IO[Unit] = {

    val combinedInterfaceDetailsJson = combinedInterfaceDetails
      .map {
        case (key, value: String) => key -> Json.fromString(value)
        case (key, value: Int) => key -> Json.fromInt(value)
        case (key, value: Boolean) => key -> Json.fromBoolean(value)
        case (key, value) => key -> Json.fromString(value.toString)
      }
      .asJson
      .noSpaces

    val query =
      sql"""
        UPDATE vm_details
        SET combined_interface_details = $combinedInterfaceDetailsJson
        WHERE internal_vm_name = $internalVmName AND cli_id = $cliId
      """.update.run

    SqlDB.transactor.use { xa =>
      query.transact(xa).attempt.map {
        case Right(_) => ()
        case Left(e) =>
          println(s"Error updating Wireguard details: ${e.getMessage}")
          throw new RuntimeException(e.getMessage)
      }
    }
  }
}
