package storage

import java.nio.file.{Files, Paths}
import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.FileIO
import com.typesafe.config.ConfigFactory

object FileSender {
  val config = ConfigFactory.load()
  val storageDirectory = config.getString("storage.directory")

  def sendChunk(fileId: String, chunkIndex: Int, serverLink: String)(implicit system: ActorSystem): Future[HttpResponse] = {
    implicit val executionContext = system.dispatcher

    val chunkPath = Paths.get(storageDirectory, s"$fileId-$chunkIndex.tmp")

    if (Files.exists(chunkPath)) {
      val chunkSource = FileIO.fromPath(chunkPath)
      val formData = Multipart.FormData(
        Multipart.FormData.BodyPart.Strict("chunk", HttpEntity(ContentTypes.`application/octet-stream`, chunkSource)),
        Multipart.FormData.BodyPart.Strict("index", HttpEntity(chunkIndex.toString)),
        Multipart.FormData.BodyPart.Strict("fileId", HttpEntity(fileId))
      )

      Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"$serverLink/upload/chunk",
          entity = formData.toEntity()
        )
      )
    } else {
      Future.failed(new Exception(s"Chunk file not found: $chunkPath"))
    }
  }

  def sendChunksParallel(fileId: String, serverLinks: Seq[String])(implicit system: ActorSystem): Future[Seq[HttpResponse]] = {

    // List all chunk files for the given fileId
    val chunkFiles = Files.list(Paths.get(storageDirectory))
      .filter(path => path.getFileName.toString.startsWith(s"$fileId-"))
      .toArray
      .map(_.toString)
      .sorted // Ensure chunks are processed in order

    val totalChunks = chunkFiles.length
    val totalServers = serverLinks.length

    // Function to send chunks in batches
    def sendBatch(startIndex: Int): Future[Seq[HttpResponse]] = {
      val batchFutures = (startIndex until math.min(startIndex + totalServers, totalChunks)).zip(serverLinks).map {
        case (chunkIndex, serverLink) =>
          val chunkPath = Paths.get(chunkFiles(chunkIndex))
          val chunkIndexNumber = chunkIndex + 1 // Extract chunk index from filename
          sendChunk(fileId, chunkIndexNumber, serverLink)
      }
      Future.sequence(batchFutures)
    }

    // Process chunks in batches
    def processChunks(startIndex: Int, responses: Seq[HttpResponse]): Future[Seq[HttpResponse]] = {
      if (startIndex >= totalChunks) {
        Future.successful(responses) // All chunks processed
      } else {
        sendBatch(startIndex).flatMap { batchResponses =>
          processChunks(startIndex + totalServers, responses ++ batchResponses)
        }
      }
    }

    processChunks(0, Seq.empty)
  }
}
