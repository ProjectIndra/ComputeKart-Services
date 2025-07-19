package Utils

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive0, Route}

object Cors {
  private val allowedOrigin = `Access-Control-Allow-Origin`.*  // Allow all origins
  private val allowedHeaders = RawHeader("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept, Authorization, bearer, ngrok-skip-browser-warning")
  private val allowedMethods = `Access-Control-Allow-Methods`(
    HttpMethods.GET, HttpMethods.POST, HttpMethods.PUT, HttpMethods.DELETE, HttpMethods.OPTIONS
  )
  private val allowCredentials = `Access-Control-Allow-Credentials`(true)

  private def addCORSHeaders: Directive0 = {
    respondWithHeaders(
      allowedOrigin,
      allowedHeaders,
      allowedMethods,
      allowCredentials
    )
  }

  def corsHandler(route: Route): Route = {
    // Match all OPTIONS requests (i.e., preflight)
    options {
      complete(HttpResponse(StatusCodes.OK).withHeaders(
        allowedOrigin,
        allowedHeaders,
        allowedMethods,
        allowCredentials
      ))
    } ~ addCORSHeaders(route)
  }
}
