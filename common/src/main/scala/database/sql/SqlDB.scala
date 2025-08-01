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
import utils.models.TempTokenModel

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
    val tasks = List(
      UserModel.createUsersTable(),
      CliModel.createCliSessionsTable(),
      VmDetailsModel.createVmDetailsTable(),
      VmStatusModel.createVmStatusTable(),
      WireguardConnectionModel.createWireguardConnectionTable(),
      ProviderConfModel.createProviderConfTable(),
      ProviderModel.createProviderTable(),
      TunnelModel.createTunnelTable(),
      TempTokenModel.createTempTokenModel()
    )

    tasks.parSequence_.void
  }
}
