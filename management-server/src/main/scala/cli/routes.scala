package cli

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cli.controllers.{CliVerificationController, CliSessionController}

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
      }
    )
}
