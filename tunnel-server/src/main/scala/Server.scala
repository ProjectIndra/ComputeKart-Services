package main

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer

import java.net.{ServerSocket, Socket}
import java.io._
import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

object TunnelServer {

  val tunnelRegistry = TrieMap[String, Socket]()

  def main(): Unit = {
    implicit val system: ActorSystem = ActorSystem("TunnelServer")
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val materializer: Materializer = Materializer(system)

    Future(startTunnelListener())

    val route =
      extractHost { hostHeader =>
        val subdomain = hostHeader.split("\\.").headOption.getOrElse("")
        tunnelRegistry.get(subdomain) match {
          case Some(socket) =>
            complete {
              Future {
                val toClient = new PrintWriter(socket.getOutputStream, true)
                val fromClient = new BufferedReader(new InputStreamReader(socket.getInputStream))

                toClient.println("GET / HTTP/1.1")
                toClient.println("Host: localhost")
                toClient.println("")

                val headerBuilder = new StringBuilder
                var contentLength = 0
                var line: String = null

                while ({ line = fromClient.readLine(); line != null && line.nonEmpty }) {
                  headerBuilder.append(line + "\r\n")
                  if (line.toLowerCase.startsWith("content-length:")) {
                    contentLength = line.split(":")(1).trim.toInt
                  }
                }

                headerBuilder.append("\r\n")

                val charBuffer = new Array[Char](contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                  val readNow = fromClient.read(charBuffer, totalRead, contentLength - totalRead)
                  if (readNow == -1) throw new IOException("Unexpected end of stream")
                  totalRead += readNow
                }

                val body = new String(charBuffer)
                val fullResponse = headerBuilder.toString() + body

                println(s"Full response from client [$subdomain]:\n$fullResponse")

                HttpResponse(
                  status = StatusCodes.OK,
                  entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, fullResponse)
                )
              }
            }

          case None =>
            complete(HttpResponse(status = StatusCodes.NotFound, entity = s"No tunnel found for subdomain: $subdomain"))
        }
      }

    Http().newServerAt("0.0.0.0", 8080).bind(route).onComplete {
      case Success(_) =>
        println("Tunnel server running on port 8080...")
      case Failure(ex) =>
        println(s"Failed to bind HTTP server: ${ex.getMessage}")
        system.terminate()
    }
  }

  def startTunnelListener(): Unit = {
    val serverSocket = new ServerSocket(9000)
    println("Listening for tunnel clients on port 9000...")

    while (true) {
      val clientSocket = serverSocket.accept()
      val reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream))
      val tunnelId = reader.readLine().trim
      println(s"Registered new tunnel: $tunnelId")
      tunnelRegistry.put(tunnelId, clientSocket)
    }
  }
}
