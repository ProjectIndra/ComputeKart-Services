package main

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.Materializer
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._

import users.UsersRoutes
import cli.CliRoutes
import providers.ProvidersRoutes
import vms.VmsRoutes
import wg.WgRoutes
import Utils.Cors.corsHandler

object Server {
  implicit val system: ActorSystem = ActorSystem("managementServer")
  implicit val materializer: Materializer = Materializer(system)
  implicit val executionContext: scala.concurrent.ExecutionContextExecutor = system.dispatcher

  val baseRoutes: Route =
    pathSingleSlash {
      get {
        complete("Welcome to the ComputeKart Management Server!")
      }
    } ~
      UsersRoutes.route ~
      CliRoutes.route ~
      ProvidersRoutes.route ~
      VmsRoutes.route ~
      WgRoutes.route

  val loggedRoutes: Route = extractRequestContext { ctx =>
    println(s"Incoming request: ${ctx.request.method.value} ${ctx.request.uri}")
    println("Headers:")
    ctx.request.headers.foreach(h => println(s"${h.name}: ${h.value}"))

    baseRoutes
  }

  val routes: Route = corsHandler(loggedRoutes)

  def run(): Unit = {
    val bindingFuture = Http().newServerAt("0.0.0.0", 4000).bindFlow(routes)

    bindingFuture
      .map { binding =>
        println(s"Server online at http://${binding.localAddress.getHostString}:${binding.localAddress.getPort}/")
      }
      .recover { case ex: Exception =>
        println(s"Failed to bind HTTP endpoint, terminating system: ${ex.getMessage}")
        system.terminate()
      }
  }
}
