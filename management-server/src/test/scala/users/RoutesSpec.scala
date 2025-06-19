package users

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import users.UsersRoutes

class RoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest {
  "UsersRoutes" should {
    "have a working /users/register route" in {
      Post("/users/register") ~> UsersRoutes.route ~> check {
        handled shouldBe true
      }
    }

    "have a working /users/login route" in {
      Post("/users/login") ~> UsersRoutes.route ~> check {
        handled shouldBe true
      }
    }
  }
}