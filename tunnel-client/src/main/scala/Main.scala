package main

import cats.effect.{ExitCode, IO, IOApp}
import tunnels._

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    args match {
      case sessionToken :: tunnelId :: host :: port :: _ =>
        startServer(sessionToken, tunnelId, host, port.toInt).as(ExitCode.Success)
      case sessionToken :: tunnelId :: _ =>
        startServer(sessionToken, tunnelId, "localhost", 6060).as(ExitCode.Success)
      case _ =>
        IO {
          println("Usage: <program> <sessionToken> <tunnelId> [host] [port]")
        }.as(ExitCode.Error)
    }
  }

  def startServer(sessionToken: String, tunnelId: String, host: String, port: Int): IO[Unit] = IO {
    TunnelClient.startTunnel(tunnelId, sessionToken, host, port)
  }
}