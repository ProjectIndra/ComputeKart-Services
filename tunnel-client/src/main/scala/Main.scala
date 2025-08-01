package main

import cats.effect.{ExitCode, IO, IOApp}
import tunnels._

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    
    args match {
      case verificationToken :: host :: port :: _ =>
        startServer(verificationToken, host, port.toInt).as(ExitCode.Success)
      case verificationToken :: tunnelId :: _ =>
        startServer(verificationToken, "localhost", 6060).as(ExitCode.Success)
      case _ =>
        IO {
          println("Usage: <program> <sessionToken> <tunnelId> [host] [port]")
        }.as(ExitCode.Error)
    }
  }

  def startServer(verificationToken: String, host: String, port: Int): IO[Unit] = IO {
    TunnelClient.startTunnel(verificationToken, host, port)
  }
}
