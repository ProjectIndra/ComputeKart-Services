package middleware

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import middleware.AuthMiddleware
import middleware.User

trait BaseController {
  def uiLoginRequired(route: User => Route): Route = AuthMiddleware.uiLoginRequired(route)
}