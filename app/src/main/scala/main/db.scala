package main

import cats.effect.{IO, Resource}
import com.typesafe.config.ConfigFactory
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts

object DB {
  // Load configuration
  private val config = ConfigFactory.load()
  private val dbConfig = config.getConfig("db")

  private val url = dbConfig.getString("url")
  private val username = dbConfig.getString("username")
  private val password = dbConfig.getString("password")
  private val driver = dbConfig.getString("driver")
  private val poolSize = dbConfig.getInt("poolSize")

  // Create a transactor as a Resource
  val transactor: Resource[IO, HikariTransactor[IO]] = for {
    ce <- ExecutionContexts.fixedThreadPool[IO](poolSize) // Connection execution context
    xa <- HikariTransactor.newHikariTransactor[IO](
      driver,
      url,
      username,
      password,
      ce
    )
  } yield xa
}