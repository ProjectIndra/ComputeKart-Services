package main

import cats.effect.{ExitCode, IO, IOApp}
import tunnels._

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    args match {
      case sessionToken :: tunnelId :: _ =>
        startServer(sessionToken, tunnelId).as(ExitCode.Success)
      case _ =>
        IO {
          println("Usage: <program> <sessionToken> <tunnelId>")
        }.as(ExitCode.Error)
    }
  }

  def startServer(sessionToken: String, tunnelId: String): IO[Unit] = IO {
    TunnelClient.startTunnel(tunnelId, sessionToken)
  }
}