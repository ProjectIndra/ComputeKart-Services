package wg

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import wg.controllers.WgController

object WgRoutes {
  val route: Route =
    pathPrefix("cli" / "wg") {
      concat(
        WgController.connectWireguard
      )
    }
}