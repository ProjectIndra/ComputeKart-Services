package cli

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cli.controllers._

object CliRoutes {
  val route: Route =
    concat(
      pathPrefix("ui") {
        concat(
          CliSessionController.routes,
          CliVerificationController.uiRoutes
        )
      },
      pathPrefix("cli" / "profile") {
        CliVerificationController.cliRoutes
      },
      pathPrefix("vms") {
        concat(
          CliDetailsController.startVmCli,
          CliDetailsController.stopVmCli,
          CliDetailsController.removeVmCli,
          CliDetailsController.forceRemoveVmCli
        )
      }
    )
}
