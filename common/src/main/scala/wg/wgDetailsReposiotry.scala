package wg

import cats.effect.IO
import doobie.implicits._
import io.circe.Json
import io.circe.syntax._
import main.SqlDB

object WgDetailsRepository {

  /** Updates Wireguard details for a VM. */
  def updateWireguardDetails(
    internalVmName: String,
    cliId: String,
    combinedInterfaceDetails: Map[String, Any]
  ): IO[Either[Throwable, Unit]] = {

    // Convert the combined interface details to JSON
    val combinedInterfaceDetailsJson = combinedInterfaceDetails
      .map {
        case (key, value: String) => key -> Json.fromString(value)
        case (key, value: Int) => key -> Json.fromInt(value)
        case (key, value: Boolean) => key -> Json.fromBoolean(value)
        case (key, value) => key -> Json.fromString(value.toString)
      }
      .asJson
      .noSpaces

    // Define the query
    val query =
      sql"""
        UPDATE vm_details
        SET combined_interface_details = $combinedInterfaceDetailsJson
        WHERE internal_vm_name = $internalVmName AND cli_id = $cliId
      """.update.run

    // Use the helper function to execute the query
    SqlDB.runUpdateQuery(query, "Update Wireguard Details")
  }
}
