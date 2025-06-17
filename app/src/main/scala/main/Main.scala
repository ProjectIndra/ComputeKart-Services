package main

import cats.effect.{ExitCode, IO, IOApp}
import users.models.UserModel

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- UserModel.createUsersTable() // Ensure the users table is created
      _ <- IO(println("Database initialized successfully."))
      _ <- startServer() // Start the Akka HTTP server
    } yield ExitCode.Success
  }

  def startServer(): IO[Unit] = IO {
    Server.run() // Start the server
  }
}