package main

import cats.effect.{ExitCode, IO, IOApp}
import main.DB

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- DB.initialize() // Initialize the database and create necessary tables
      _ <- IO(println("Database initialized successfully."))
      _ <- startServer() // Start the Akka HTTP server
    } yield ExitCode.Success
  }

  def startServer(): IO[Unit] = IO {
    Server.run() // Start the server
  }
}