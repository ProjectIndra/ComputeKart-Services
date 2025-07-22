package storage

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.{Files, Paths}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.scaladsl.FileIO
import spray.json._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import spray.json._
import JsonProtocol._

case class ChunkMetadata(chunkIndex: Int, size: Long, serverLinks: Seq[String])
case class FileMetadata(fileID: String, chunks: Seq[ChunkMetadata])

object JsonProtocol extends DefaultJsonProtocol {
  implicit val chunkMetadataFormat: RootJsonFormat[ChunkMetadata] = jsonFormat3(ChunkMetadata)
  implicit val fileMetadataFormat: RootJsonFormat[FileMetadata] = jsonFormat2(FileMetadata)
}

object Directory {
  private val config = ConfigFactory.load()
  private val storageDir = config.getString("storage.directory")
  private val chunkSize = config.getLong("storage.chunkSize")
  private val managerLink = config.getString("storage.managerLink")
  private val replicationFactor = config.getInt("storage.replicationFactor")
  private val myLink = config.getString("storage.myLink")

  implicit val system: ActorSystem = ActorSystem()
  implicit val executionContext: ExecutionContext = system.dispatcher

  /**
   * Validates a directory path and creates it if it doesn't exist.
   * @param dirPath Path to the directory
   */
  private def validateOrCreateDirectory(dirPath: String): Unit = {
    val dir = new File(dirPath)
    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        throw new RuntimeException(s"Failed to create directory: $dirPath")
      }
    } else if (!dir.isDirectory) {
      throw new IllegalArgumentException(s"Path '$dirPath' is not a valid directory")
    }
  }

  /**
   * Reads a local file, splits it into chunks, and stores the chunks in the owner directory.
   * @param filePath Path to the local file
   * @return File ID (name of the file)
   */
  private def addFileToOwnDirectory(filePath: String): String = {
    val file = new File(filePath)
    if (!file.exists() || !file.isFile) {
      throw new IllegalArgumentException(s"Path '$filePath' is not a valid file")
    }

    validateOrCreateDirectory(storageDir)

    val fileInputStream = new FileInputStream(file)
    val buffer = new Array[Byte](chunkSize.toInt)
    var bytesRead = 0
    var chunkIndex = 0

    while ({ bytesRead = fileInputStream.read(buffer); bytesRead != -1 }) {
      val chunkFile = new File(storageDir, s"${file.getName}-$chunkIndex.tmp")
      val fileOutputStream = new FileOutputStream(chunkFile)
      fileOutputStream.write(buffer, 0, bytesRead)
      fileOutputStream.close()
      chunkIndex += 1
    }

    fileInputStream.close()

    // Save metadata to a JSON file
    val metadataFile = new File(storageDir, s"metadata-${file.getName}.json")
    val metadata = JsObject(
      "originalFileName" -> JsString(file.getName),
      "originalFilePath" -> JsString(file.getAbsolutePath)
    )
    val fileOutputStream = new FileOutputStream(metadataFile)
    fileOutputStream.write(metadata.prettyPrint.getBytes)
    fileOutputStream.close()

    file.getName // Return the file ID
  }

  /**
   * Gets the best storage servers ready to accept chunks from the management server.
   * @param numServers Number of servers required
   * @return Future[Seq[String]] List of server links
   */
  private def getBestStorageServers(numServers: Int): Future[Seq[String]] = {
    if (numServers <= 0) {
      Future.failed(new IllegalArgumentException("Number of servers must be greater than 0"))
    } else {
      val requestEntity = HttpEntity(
        ContentTypes.`application/json`,
        s"""{"count": $numServers}"""
      )

      Http().singleRequest(
        HttpRequest(
          method = HttpMethods.POST,
          uri = s"$managerLink/get-servers",
          entity = requestEntity
        )
      ).flatMap { response =>
        if (response.status.isSuccess()) {
          Unmarshal(response.entity).to[String].map { responseBody =>
            val json = responseBody.parseJson.asJsObject
            json.fields("servers").convertTo[Seq[String]]
          }
        } else {
          Future.failed(new RuntimeException(s"Failed to fetch servers: ${response.status}"))
        }
      }
    }
  }

  /**
   * Adds a file to the network by splitting it into chunks, storing locally, and sending chunks to storage servers.
   * @param filePath Path to the local file
   */
  def addFileToNetwork(filePath: String): Future[FileMetadata] = {
    val fileId = addFileToOwnDirectory(filePath)

    // Read metadata file
    val metadataFile = new File(storageDir, s"metadata-$fileId.json")
    if (!metadataFile.exists()) {
      throw new RuntimeException(s"Metadata file not found for file ID: $fileId")
    }
    val metadataContent = new String(Files.readAllBytes(metadataFile.toPath))
    val metadataJson = metadataContent.parseJson.asJsObject

    // Extract original file name and path
    val originalFileName = metadataJson.fields("originalFileName").convertTo[String]
    val originalFilePath = metadataJson.fields("originalFilePath").convertTo[String]

    // List all chunks created for the file
    val chunkFiles = Files.list(Paths.get(storageDir))
      .filter(path => path.getFileName.toString.startsWith(s"$fileId-"))
      .toArray
      .map(_.toString)
      .sorted

    val chunks = chunkFiles.zipWithIndex.map { case (chunkPath, index) =>
      val chunkSize = Files.size(Paths.get(chunkPath))
      ChunkMetadata(index, chunkSize, Seq(myLink)) // Include owner's link initially
    }

    getBestStorageServers(replicationFactor - 1).flatMap { serverLinks =>
      if (serverLinks.isEmpty) {
        Future.failed(new RuntimeException("No storage servers available"))
      } else {
        FileSender.sendChunksParallel(fileId, serverLinks).map { responses =>
          responses.zipWithIndex.foreach { case (response, index) =>
            if (!response.status.isSuccess()) {
              throw new RuntimeException(s"Failed to send chunk: ${response.status}")
            } else {
              // Assign server links to chunks, including the owner's link
              chunks(index) = chunks(index).copy(serverLinks = managerLink +: serverLinks)
            }
          }

          // Save final metadata to the same path as the original metadata file
          val finalMetadata = JsObject(
            "fileID" -> JsString(fileId),
            "originalFileName" -> JsString(originalFileName),
            "originalFilePath" -> JsString(originalFilePath),
            "chunks" -> JsArray(chunks.map(_.toJson).toVector)
          )
          val fileOutputStream = new FileOutputStream(metadataFile) // Overwrite the original metadata file
          fileOutputStream.write(finalMetadata.prettyPrint.getBytes)
          fileOutputStream.close()

          // Return metadata after successful upload
          FileMetadata(fileId, chunks)
        }
      }
    }.recover {
      case ex: Exception =>
        throw new RuntimeException(s"Failed to add file to network: ${ex.getMessage}")
    }
  }
}