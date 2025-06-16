package main

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer

object Server{
    implicit val system: ActorSystem = ActorSystem("managementServer")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val route =
        path("health") {
            get {
                complete("Server is up and running!")
            }
        }

    def run(): Unit = {
        val bindingFuture = Http().bindAndHandle(route, "localhost", 5000)

        bindingFuture.map { binding =>
            println(s"Server online at http://${binding.localAddress.getHostString}:${binding.localAddress.getPort}/")
        }.recover {
            case ex: Exception =>
                println(s"Failed to bind HTTP endpoint, terminating system: ${ex.getMessage}")
                system.terminate()
        }
    }
}