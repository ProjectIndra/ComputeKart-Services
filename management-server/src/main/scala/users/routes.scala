package users

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import users.controllers.{RegisterController, LoginController, UserInfoController}

object UsersRoutes {
  val route: Route =
    concat(
      RegisterController.register,
      LoginController.login,
      pathPrefix("ui" / "profile") {
        UserInfoController.routes
      }
    )
}
