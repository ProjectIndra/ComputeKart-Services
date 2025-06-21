package vms

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import vms.controllers._

object VmsRoutes {
  val route: Route =
    concat(
      pathPrefix("vms") {
        concat(
            LaunchVmController.launchVm,
        )
      }
    )
}
