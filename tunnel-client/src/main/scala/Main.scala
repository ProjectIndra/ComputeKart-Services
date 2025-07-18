package main

import cats.effect.{ExitCode, IO, IOApp}
import tunnels._

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- SqlDB.initialize() // Initialize the database and create necessary tables
      _ <- IO(println("Database initialized successfully."))
      _ <- startServer() // Start the Akka HTTP server
    } yield ExitCode.Success
    
    args match {
      case VerificationToken :: host :: port :: _ =>
        startServer(VerificationToken, host, port.toInt).as(ExitCode.Success)
      case sessionToken :: tunnelId :: _ =>
        startServer(VerificationToken, "localhost", 6060).as(ExitCode.Success)
      case _ =>
        IO {
          println("Usage: <program> <sessionToken> <tunnelId> [host] [port]")
        }.as(ExitCode.Error)
    }
  }

  def startServer(VerificationToken: String, host: String, port: Int): IO[Unit] = IO {
    TunnelClient.startTunnel(VerificationToken, host, port)
  }
}
