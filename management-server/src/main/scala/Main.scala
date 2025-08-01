package main

import cats.effect.{ExitCode, IO, IOApp}
import doobie.hikari.HikariTransactor

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] = {
    for {
      xa <- SqlDB.initializeTransactor().allocated
      _ <- SqlDB.setTransactor(xa._1) // xa._1 is the transactor, xa._2 is the shutdown hook
      // _ <- SqlDB.initialize()
      _ <- startServer()
    } yield ExitCode.Success

  }

  def startServer(): IO[Unit] = IO {
    Server.run()
  }
}
