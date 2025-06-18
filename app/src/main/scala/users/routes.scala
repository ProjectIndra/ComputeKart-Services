package users

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import users.controllers.{RegisterController, LoginController}

object UsersRoutes {
  val route: Route =
    pathPrefix("users") {
      concat(
        RegisterController.register,
        LoginController.login
      )
    }
}