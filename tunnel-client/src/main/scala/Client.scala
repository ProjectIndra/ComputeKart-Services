package main

import java.net.Socket
import java.io._

object TunnelClient {
  def main(): Unit = {
    val tunnelId = "my-app"
    val tunnelSocket = new Socket("localhost", 9000)

    val tunnelIn = new BufferedReader(new InputStreamReader(tunnelSocket.getInputStream))
    val tunnelOut = new PrintWriter(tunnelSocket.getOutputStream, true)

    tunnelOut.println(tunnelId)
    println(s"Registered tunnel ID: $tunnelId")

    while (true) {
      try {
        val tunnelRequest = tunnelIn.readLine()
        println(s"Received from server: $tunnelRequest")

        val localSocket = new Socket("localhost", 3000)
        val localIn = new BufferedReader(new InputStreamReader(localSocket.getInputStream))
        val localOut = new PrintWriter(localSocket.getOutputStream, true)

        localOut.println("GET / HTTP/1.1")
        localOut.println("Host: localhost")
        localOut.println("")

        val responseBuilder = new StringBuilder
        var line: String = null
        while ({ line = localIn.readLine(); line != null }) {
          responseBuilder.append(line + "\n")
        }

        val fullResponse = responseBuilder.toString()
        println(s"Forwarded full response:\n$fullResponse")

        tunnelOut.println(fullResponse)

        localSocket.close()
      } catch {
        case e: Exception =>
          println(s"Error: ${e.getMessage}")
          Thread.sleep(1000)
      }
    }
  }
}
