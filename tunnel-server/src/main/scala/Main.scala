package main

import cats.effect.{ExitCode, IO, IOApp}
import main.SqlDB

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- SqlDB.initialize() // Initialize the database and create necessary tables
      _ <- IO(println("Database initialized successfully."))
      _ <- startServer() // Start the Akka HTTP server
    } yield ExitCode.Success
  }

  def startServer(): IO[Unit] = IO {
    TunnelServer.main() // Start the server
  }
}