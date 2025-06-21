package utils

import cats.effect.IO

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import org.mindrot.jbcrypt.BCrypt
import com.typesafe.config.ConfigFactory
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import scala.util.{Failure, Success, Try}
import java.time.Instant

object CryptoUtils {

  private val config = ConfigFactory.load()
  private val secretKeyBytes = Base64.getDecoder.decode(config.getString("crypto.secretKey"))

  // Validate the key length (16, 24, or 32 bytes)
  require(
    secretKeyBytes.length == 16 || secretKeyBytes.length == 24 || secretKeyBytes.length == 32,
    s"Invalid AES key length: ${secretKeyBytes.length} bytes. Key must be 16, 24, or 32 bytes."
  )

  private val cipher = Cipher.getInstance("AES")

  // Encrypt a string using AES
  def encrypt(data: String): String = {
    val keySpec = new SecretKeySpec(secretKeyBytes, "AES")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec)
    Base64.getEncoder.encodeToString(cipher.doFinal(data.getBytes("UTF-8")))
  }

  // Decrypt a string using AES
  def decrypt(data: String): String = {
    val keySpec = new SecretKeySpec(secretKeyBytes, "AES")
    cipher.init(Cipher.DECRYPT_MODE, keySpec)
    new String(cipher.doFinal(Base64.getDecoder.decode(data)), "UTF-8")
  }

  // Hash a password using BCrypt
  def hashPassword(password: String): String = {
    BCrypt.hashpw(password, BCrypt.gensalt())
  }

  // Verify a password against a hashed password
  def verifyPassword(password: String, hashedPassword: String): Boolean = {
    BCrypt.checkpw(password, hashedPassword)
  }

  // Encode a JWT token
  def encodeJwt(userId: String, username: String, email: String): String = {
    val claim = JwtClaim(
      content = s"""{"user_id":"$userId","username":"$username","email":"$email"}""",
      expiration = Some(Instant.now.getEpochSecond + 86400) // Token expires in 24 hours
    )
    Jwt.encode(claim, config.getString("crypto.secretKey"), JwtAlgorithm.HS256)
  }

  def decodeJwt(token: String): IO[Map[String, String]] = {
    IO {
      Jwt.decode(token, config.getString("crypto.secretKey"), Seq(JwtAlgorithm.HS256)) match {
        case Success(claim) =>
          io.circe.parser.parse(claim.content).flatMap(_.as[Map[String, String]]) match {
            case Right(data) => data
            case Left(_) => throw new Exception("Failed to parse JWT content")
          }
        case Failure(_) => throw new Exception("Invalid JWT token")
      }
    }
  }
}
