package tunnels

import java.net.Socket
import java.io._

object TunnelClient {
  def startTunnel(verificationToken: String, host: String, port: Int): Unit = {
    val tunnelSocket = new Socket("34.29.118.22", 9000)

    val tunnelIn = new BufferedReader(new InputStreamReader(tunnelSocket.getInputStream))
    val tunnelOut = new PrintWriter(tunnelSocket.getOutputStream, true)

    // Send verification token
    tunnelOut.println(verificationToken)
    println(s"[Client] Sent verification token: $verificationToken")

    // Wait for server response
    val response = tunnelIn.readLine()
    println(s"[Client] Server response: $response")

    if (response == "SUCCESS") {
      println("[Client] Tunnel registration successful")

      while (true) {
        try {
          println("[Client] Waiting for tunnel request...")

          val requestBuilder = new StringBuilder()
          var line: String = null

          // Read full request headers from server
          while ({ line = tunnelIn.readLine(); line != null && line.nonEmpty }) {
            requestBuilder.append(line).append("\r\n")
          }

          if (requestBuilder.isEmpty) {
            println("[Client] Received empty request, skipping...")
            Thread.sleep(500)
          } else {
            val fullRequest = requestBuilder.toString() + "\r\n"
            println(s"[Client] Received request:\n$fullRequest")

            val localSocket = new Socket(host, port)
            val localIn = new BufferedReader(new InputStreamReader(localSocket.getInputStream))
            val localOut = new PrintWriter(localSocket.getOutputStream, true)

            // Forward to local service
            localOut.print(fullRequest)
            localOut.flush()

            // Read full response from local service
            val localResponseBuilder = new StringBuilder()
            while ({ line = localIn.readLine(); line != null }) {
              localResponseBuilder.append(line).append("\r\n")
            }

            val localResponse = localResponseBuilder.toString()
            println(s"[Client] Sending response back to server:\n$localResponse")

            tunnelOut.print(localResponse)
            tunnelOut.flush()

            localSocket.close()
          }
        } catch {
          case e: Exception =>
            println(s"[Client] Error: ${e.getMessage}")
            Thread.sleep(1000)
        }
      }
    } else {
      println(s"[Client] Tunnel registration failed: $response")
      tunnelSocket.close()
    }
  }
}
