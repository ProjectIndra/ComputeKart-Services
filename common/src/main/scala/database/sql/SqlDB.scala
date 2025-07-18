package main

import cats.effect.{IO, Resource}
import com.typesafe.config.ConfigFactory
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts

import users.models.UserModel
import cli.models.CliModel
import vms.models.{VmDetailsModel, VmStatusModel, WireguardConnectionModel}
import providers.models.{ProviderConfModel, ProviderModel}
import tunnels.models.TunnelModel

object SqlDB {
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

  // method to initalize the all the required tables
  def initialize(): IO[Unit] = {
    for {
      _ <- UserModel.createUsersTable()
      _ <- CliModel.createCliSessionsTable()
      _ <- VmDetailsModel.createVmDetailsTable()
      _ <- VmStatusModel.createVmStatusTable()
      _ <- WireguardConnectionModel.createWireguardConnectionTable()
      _ <- ProviderConfModel.createProviderConfTable()
      _ <- ProviderModel.createProviderTable()
      _ <- TunnelModel.createTunnelTable()
    } yield ()
  }
}
