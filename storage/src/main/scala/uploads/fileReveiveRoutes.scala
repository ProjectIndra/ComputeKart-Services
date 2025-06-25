package storage

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import akka.stream.scaladsl._
import akka.util.ByteString
import java.nio.file.{Files, Paths, StandardOpenOption}
import com.typesafe.config.ConfigFactory

object FileReveiveRoutes {
  private val config = ConfigFactory.load()
  private val storageDirectory = config.getString("storage.directory")

  val route: Route =
    concat(
      path("upload/chunk") {
        post {
          extractRequestContext { ctx =>
            implicit val materializer = ctx.materializer
            implicit val executionContext = ctx.executionContext

            entity(as[Multipart.FormData]) { formData =>
              val future = formData.parts
                .mapAsync(1) {
                  case part if part.name == "chunk" =>
                    val fileId = part.filename.getOrElse("unknown")
                    val index = part.headers.find(_.name == "index").map(_.value.toInt).getOrElse(0)
                    val chunkPath = Paths.get(storageDirectory, s"$fileId-$index.tmp")

                    part.entity.dataBytes.runWith(FileIO.toPath(chunkPath)).map { _ =>
                      HttpResponse(StatusCodes.OK)
                    }

                  case _ =>
                    Future.successful(HttpResponse(StatusCodes.BadRequest))
                }
                .runFold(Seq.empty[HttpResponse])(_ :+ _)

              onComplete(future) { _ =>
                complete(HttpResponse(StatusCodes.OK))
              }
            }
          }
        }
      }
    )
}
