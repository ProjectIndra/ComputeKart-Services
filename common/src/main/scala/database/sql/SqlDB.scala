package main

import cats.effect.{IO, Resource}
import com.typesafe.config.ConfigFactory
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor

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

  // Global shared transactor instance (lazy mutable)
  private var transactorInstance: HikariTransactor[IO] = _

  // Getter to access the initialized transactor
  def transactor: HikariTransactor[IO] = {
    if (transactorInstance == null)
      throw new Exception("Transactor has not been initialized yet.")
    transactorInstance
  }

  // Used once to initialize the transactor
  def initializeTransactor(): Resource[IO, HikariTransactor[IO]] = {
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](poolSize)
      xa <- HikariTransactor.newHikariTransactor[IO](
        driver,
        url,
        username,
        password,
        ce
      )
      configuredXa <- Resource.eval {
        xa.configure { ds =>
          IO {
            println("[Hikari] Configuring connection pool")
            ds.setConnectionTestQuery("SELECT 1")
            ds.setMaximumPoolSize(poolSize)
            ds.setMinimumIdle(math.max(1, poolSize / 2))
            ds.setIdleTimeout(60000)         // 1 min
            ds.setValidationTimeout(5000)    // 5 sec
            ds.setKeepaliveTime(300000)      // 5 min (MySQL drops idle connections often)
            ds.setMaxLifetime(1800000)       // 30 min
            ds.setLeakDetectionThreshold(10000) // Warn if query hangs 10s+
          }
        }.as(xa)
      }
    } yield configuredXa
  }

  // Sets the global transactor instance (should be called once)
  def setTransactor(xa: HikariTransactor[IO]): IO[Unit] = IO {
    transactorInstance = xa
  }

  // Initialize all tables
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

  def runQueryEither[A](io: doobie.ConnectionIO[Option[A]], label: String = "DB Query"): IO[Either[Throwable, A]] = {
    val t0 = System.currentTimeMillis()
    io.transact(transactor).attempt.map {
      case Right(Some(value)) =>
        println(s"[$label] Success. Took ${System.currentTimeMillis() - t0}ms")
        Right(value)
      case Right(None) =>
        println(s"[$label] No result found. Took ${System.currentTimeMillis() - t0}ms")
        Left(new Exception(s"$label failed: No result found"))
      case Left(error) =>
        println(s"[$label] Failed: ${error.getMessage}")
        Left(error)
    }
  }

  // Generic helper to run any doobie query with logging and error handling
  def runQuery[A](io: doobie.ConnectionIO[A], label: String = "DB Query"): IO[Either[Throwable, A]] = {
    val t0 = System.currentTimeMillis()
    io.transact(transactor).attempt.map {
      case Right(value) =>
        println(s"[$label] Success. Took ${System.currentTimeMillis() - t0}ms")
        Right(value)
      case Left(error) =>
        println(s"[$label] Failed: ${error.getMessage}")
        Left(error)
    }
  }

  // Optional query runner for queries returning Option[A]
  def runOptionalQuery[A](io: doobie.ConnectionIO[Option[A]], label: String = "DB Optional Query"): IO[Option[A]] = {
    val t0 = System.currentTimeMillis()
    io.transact(transactor).attempt.flatMap {
      case Right(opt) =>
        println(s"[$label] Completed in ${System.currentTimeMillis() - t0}ms")
        IO.pure(opt)
      case Left(error) =>
        println(s"[$label] Failed: ${error.getMessage}")
        IO.raiseError(new Exception(s"$label failed: ${error.getMessage}"))
    }
  }

  def runCountQuery(query: doobie.ConnectionIO[Int], label: String = "Count Query"): IO[Either[Throwable, Boolean]] = {
    val t0 = System.currentTimeMillis()
    query.transact(transactor).attempt.map {
      case Right(count) =>
        println(s"[$label] Success. Took ${System.currentTimeMillis() - t0}ms")
        Right(count > 0) // Convert Int to Boolean
      case Left(error) =>
        println(s"[$label] Failed: ${error.getMessage}")
        Left(error)
    }
  }

  def runUpdateQuery(query: doobie.ConnectionIO[Int], label: String = "Update Query"): IO[Either[Throwable, Unit]] = {
    val t0 = System.currentTimeMillis()
    query.transact(transactor).attempt.map {
      case Right(_) =>
        println(s"[$label] Success. Took ${System.currentTimeMillis() - t0}ms")
        Right(()) // Convert the result to Unit
      case Left(error) =>
        println(s"[$label] Failed: ${error.getMessage}")
        Left(error)
    }
  }

  def runSchemaQuery(query: doobie.ConnectionIO[Int], label: String = "Schema Query"): IO[Int] = {
    val t0 = System.currentTimeMillis()
    query.transact(transactor).attempt.flatMap {
      case Right(rowsAffected) =>
        println(s"[$label] Success. Took ${System.currentTimeMillis() - t0}ms")
        IO.pure(rowsAffected) // Return the number of rows affected
      case Left(error) =>
        println(s"[$label] Failed: ${error.getMessage}")
        IO.raiseError(new Exception(s"$label failed: ${error.getMessage}"))
    }
  }
}
