package users

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object UsersRoutes {
  def route:Route =
    path("register") {
      post {
        complete("User registered")
      }
    } ~
    path("login") {
      post {
        complete("User logged in")
      }
    }
}