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

import providers.ProviderDetailsRepository

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

  /**
    * Starts a listener for tunnel clients on port 9000.
    * It accepts connections, reads the tunnel ID and session token,
    * verifies the session token, and registers the tunnel if valid.
    */

  def startTunnelListener(): Unit = {
    val serverSocket = new ServerSocket(9000)
    println("Listening for tunnel clients on port 9000...")

    while (true) {
      val clientSocket = serverSocket.accept()
      val reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream))
      val line = reader.readLine().trim
      // Split the line into tunnelId and sessionToken
      val parts = line.split(",", 2)
      val tunnelAndProviderId = parts(0)
      val providerId = tunnelAndProviderId.split("->-").headOption.getOrElse("")
      val tunnelId = tunnelAndProviderId.split("->-").lastOption.getOrElse("")
      val sessionToken = if (parts.length > 1) parts(1) else ""
      println(s"Registered new tunnel: $tunnelId")
      println(s"Session token: $sessionToken")
      // first verify the session token and if it's valid then register the tunnel
      val isTunnelClientVerified: Boolean = verifyTunnelSession(providerId, sessionToken)
      println("isTunnelClientVerified",isTunnelClientVerified)
      if (isTunnelClientVerified) {
        tunnelRegistry.put(tunnelId, clientSocket)
      } else {
        clientSocket.close()
      }
    }
  }

  /**
    * Verifies the tunnel session token for the given provider.
    * @param providerId The provider ID.
    * @param sessionToken The session token to verify.
    * @return true if the session token is valid, false otherwise.
    */
  def verifyTunnelSession(providerId: String, sessionToken: String): Boolean = {
    ProviderDetailsRepository.getProviderUrlAndToken(providerId).unsafeRunSync() match {
      case Right((_, verificationToken)) =>
        if (verificationToken == sessionToken) {
          println(s"Tunnel session verified for provider I  D: $providerId")
          true
        } else {
          println(s"Invalid session token for provider ID: $providerId")
          false
        }
      case Left(error) =>
        println(s"Error verifying tunnel session for provider ID: $providerId - $error")
        false
    }
  }
}
