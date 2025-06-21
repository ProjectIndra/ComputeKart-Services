package providers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object ProvidersRoutes {
    val route: Route = 
        concat(
            pathPrefix("providerServer"){

            }
        )
}