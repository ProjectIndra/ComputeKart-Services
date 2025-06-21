package users

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import users.controllers._

object UsersRoutes {
  val route: Route =
    concat(
      RegisterController.register,
      LoginController.login,
      pathPrefix("ui" / "profile") {
        UserInfoController.routes
      },
      pathPrefix("ui" / "providers") {
        concat(
          UserProviderDetailsController.getUserProviderDetails
        )
      }
    )
}
