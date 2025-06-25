package storage

import java.io.File
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import scala.concurrent.Future

object FileChunker {
  /**
   * Splits a file into ByteString chunks of given size.
   * @param file  the file to chunk
   * @param chunkSize size in bytes
   * @return a Source of (chunkIndex, ByteString)
   */
  def chunkFile(file: File, chunkSize: Int): Source[(Int, ByteString), Any] = {
    FileIO.fromPath(file.toPath, chunkSize)
      .zipWithIndex
      .map { case (bs, idx) => (idx.toInt, bs) }
  }
}