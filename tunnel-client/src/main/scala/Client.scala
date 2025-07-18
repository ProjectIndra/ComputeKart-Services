package tunnels

import java.net.Socket
import java.io._

object TunnelClient {
  def startTunnel(VerificationToken: String, host: String, port: Int): Unit = {
    val tunnelSocket = new Socket("localhost", 9000)

    val tunnelIn = new BufferedReader(new InputStreamReader(tunnelSocket.getInputStream))
    val tunnelOut = new PrintWriter(tunnelSocket.getOutputStream, true)

    // Send both tunnelId and sessionToken, separated by a comma
    tunnelOut.println(s"$VerificationToken")
    println(s"Registered tunnel with verification token: $VerificationToken")

    while (true) {
      try {
        val tunnelRequest = tunnelIn.readLine()
        println(s"Received from server: $tunnelRequest")

        val localSocket = new Socket(host, port)
        val localIn = new BufferedReader(new InputStreamReader(localSocket.getInputStream))
        val localOut = new PrintWriter(localSocket.getOutputStream, true)

        localOut.println("GET / HTTP/1.1")
        localOut.println(s"Host: ${host}:${port}")
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
