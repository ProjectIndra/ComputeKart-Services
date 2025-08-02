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

import cats.effect.unsafe.implicits.global
import tunnels.TunnelDetailsRepository._

object TunnelServer {

  val tunnelRegistry = TrieMap[String, Socket]()

  def main(): Unit = {
    implicit val system: ActorSystem = ActorSystem("TunnelServer")
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val materializer: Materializer = Materializer(system)

    Future(startTunnelListener())

    val route =
      extractHost { hostHeader =>
        val knownSuffixes = List(".127.0.0.1.nip.io", ".localhost", ".127.0.0.1.sslip.io", ".computekart.com")
        val subdomain = knownSuffixes.find(suffix => hostHeader.endsWith(suffix)) match {
          case Some(suffix) => hostHeader.stripSuffix(suffix)
          case None => hostHeader
        }
        tunnelRegistry.get(subdomain) match {
          case Some(socket) =>
            complete {
              Future {
                try {
                  val toClient = new PrintWriter(socket.getOutputStream, true)
                  val fromClient = new BufferedReader(new InputStreamReader(socket.getInputStream))

                  val httpRequest =
                    """GET / HTTP/1.1
                      |Host: localhost
                      |
                      |""".stripMargin.replace("\n", "\r\n")

                  println(s"[Server] Sending HTTP request to tunnel [$subdomain]:\n$httpRequest")

                  // Send request to tunnel client
                  toClient.print(httpRequest)
                  toClient.flush()

                  // Read response from tunnel client
                  val responseBuilder = new StringBuilder()
                  var line: String = null

                  // Read headers
                  while ({ line = fromClient.readLine(); line != null && line.nonEmpty }) {
                    responseBuilder.append(line).append("\r\n")
                  }
                  responseBuilder.append("\r\n")

                  // Check for Content-Length
                  val headers = responseBuilder.toString().split("\r\n").map(_.toLowerCase)
                  val contentLengthOpt = headers.find(_.startsWith("content-length:")).map(_.split(":")(1).trim.toInt)

                  val body = contentLengthOpt.map { len =>
                    val bodyChars = new Array[Char](len)
                    var readTotal = 0
                    while (readTotal < len) {
                      val readNow = fromClient.read(bodyChars, readTotal, len - readTotal)
                      if (readNow == -1) throw new IOException("Unexpected end of stream")
                      readTotal += readNow
                    }
                    new String(bodyChars)
                  }.getOrElse("")

                  val fullResponse = responseBuilder.toString() + body
                  println(s"[Server] Received full response from tunnel [$subdomain]:\n$fullResponse")

                  HttpResponse(
                    status = StatusCodes.OK,
                    entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, fullResponse)
                  )
                } catch {
                  case ex: Exception =>
                    println(s"[Server] Error handling request for [$subdomain]: ${ex.getMessage}")
                    HttpResponse(StatusCodes.InternalServerError, entity = "Internal Error")
                }
              }
            }

          case None =>
            complete(HttpResponse(status = StatusCodes.NotFound, entity = s"No tunnel found for subdomain: $subdomain"))
        }
      }

    Http().newServerAt("0.0.0.0", 8080).bind(route).onComplete {
      case Success(_) =>
        println("[Server] Tunnel server running on port 8080...")
      case Failure(ex) =>
        println(s"[Server] Failed to bind HTTP server: ${ex.getMessage}")
        system.terminate()
    }
  }

  def startTunnelListener(): Unit = {
    val serverSocket = new ServerSocket(9000)
    println("[Server] Listening for tunnel clients on port 9000...")

    while (true) {
      val clientSocket = serverSocket.accept()
      val reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream))
      val writer = new PrintWriter(clientSocket.getOutputStream, true)
      val line = reader.readLine()

      if (line == null || line.trim.isEmpty) {
        println("[Server] Received empty input. Closing client connection.")
        clientSocket.close()
      } else {
        val sessionToken = line.trim
        val result = verifyTunnelToken(sessionToken).unsafeRunSync()

        result match {
          case Right(tunnelDetails) =>
            val tunnelAndUsername = s"${tunnelDetails.tunnelNo}-${tunnelDetails.username}"
            tunnelRegistry.put(tunnelAndUsername, clientSocket)
            println(s"[Server] Tunnel registered: $tunnelAndUsername")
            writer.println("SUCCESS")

          case Left(error) =>
            println(s"[Server] Error verifying session token: ${error.getMessage}")
            writer.println(s"FAILURE: ${error.getMessage}")
            clientSocket.close()
        }
      }
    }
  }
}
