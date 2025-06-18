package utils

import io.circe.generic.auto._
import io.circe.syntax._

case class ErrorResponse(error: String)