package providers

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import providers.controllers._

object ProvidersRoutes {
  val route: Route =
    concat(
      pathPrefix("providerServer") {
        concat(
          VerifyProviderTokenController.verifyProviderToken,
          ProviderConfigController.getConfig
        )
      },
      pathPrefix("ui" / "providers") {
        concat(
          ProviderClientDetailsController.providerClientDetails,
          ProviderConfigController.updateConfig
        )
      },
      pathPrefix("providers") {
        concat(
          QueryVmCreationController.queryVmCreation,
          ProviderDetilsController.providersLists,
          ProviderDetilsController.providersDetails
        )
      }
    )
}
