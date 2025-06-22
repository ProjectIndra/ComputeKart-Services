package main

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.Materializer
import akka.http.scaladsl.server.Directives._

import users.UsersRoutes
import cli.CliRoutes
import providers.ProvidersRoutes
import vms.VmsRoutes
import wg.WgRoutes

object Server {
  implicit val system: ActorSystem = ActorSystem("managementServer")
  implicit val materializer: Materializer = Materializer(system)
  implicit val executionContext: scala.concurrent.ExecutionContextExecutor = system.dispatcher

  val routes =
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

  def run(): Unit = {
    val bindingFuture = Http().newServerAt("localhost", 5000).bindFlow(routes)

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
