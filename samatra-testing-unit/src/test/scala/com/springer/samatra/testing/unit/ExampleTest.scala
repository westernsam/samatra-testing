package com.springer.samatra.testing.unit

import java.net.URLEncoder.encode
import java.time.{Clock, LocalDate, ZoneId, ZoneOffset}
import javax.servlet.http.{Cookie, HttpServletRequest, HttpServletResponse}

import com.springer.samatra.routing.Routings.{Controller, Routes}
import com.springer.samatra.routing.StandardResponses._
import com.springer.samatra.testing.unit.ControllerTestHelpers._
import org.scalatest.FunSpec
import org.scalatest.Matchers._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Future

class ExampleTest extends FunSpec with ScalaFutures {

  val routes: Routes = new Controller {

    import com.springer.samatra.routing.FutureResponses.Implicits.fromFuture
    import com.springer.samatra.routing.StandardResponses.Implicits.fromString

    import scala.concurrent.ExecutionContext.Implicits.global

    put("/put") { req =>
      Future {
        Redirect(req.queryStringParamValue("to"))
      }
    }

    get("/error") { _ =>
      Future {
        Halt(500, Some(new RuntimeException("Error message")))
      }
    }

    get("/request-response") { req =>
      (_: HttpServletRequest, resp: HttpServletResponse) => {
        resp.setDateHeader("Date", LocalDate.of(2017, 5, 18).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli)
        resp.setStatus(200)
        resp.getWriter.print("sam")
      }
    }

    get("/hello/:name") { req =>
      Future {
        WithCookies(Seq(AddCookie("cookie", req.cookie("cookie").get))) {
          WithHeaders("a" -> "b") {
            req.captured("name")
          }
        }
      }
    }
  }

  describe("An example of unit testing controllers") {
    implicit val clock: Clock = Clock.tickMinutes(ZoneId.of("GMT"))

    it("should put") {
      whenReady(routes.put(
        "/put",
        body = encode("to=/xml/hi", "UTF-8").getBytes,
        headers = Map("Content-Type" -> Seq("application/x-www-form-urlencoded")))) { result =>

        val (statusCode, headers, _, _) = result.run()
        statusCode shouldBe 302
        headers("Location") shouldBe Seq("/xml/hi")
      }
    }

    it("should test with helper methods") {
      whenReady(routes.get("/request-response")) { result =>
        val (statusCode, headers, _, body) = result.run()

        statusCode shouldBe 200
        headers("Date") shouldBe Seq("Thu, 18 05 2017 12:00:00 GMT")
        new String(body) shouldBe "sam"
      }
    }

    it("should test future string") {
      whenReady(routes.get("/hello/sam", cookies = Seq(new Cookie("cookie", "expectedValue")))) { result =>
        result shouldBe WithCookies(Seq(AddCookie("cookie", "expectedValue"))) {
          WithHeaders("a" -> "b") {
            StringResp("sam")
          }
        }
      }
    }

    it("returns 404 on no match") {
      whenReady(routes.get("/nomatch")) { result =>
        result shouldBe Halt(404)
      }
    }

    it("writes errors to output stream") {
      whenReady(routes.get("/error")) { result =>
        val (statusCode, _, _, body) = result.run()

        statusCode shouldBe 500
        new String(body) should include("Error message")
      }
    }

    it("returns 405 on on MethodNotAllowed") {
      whenReady(routes.put("/hello/sam", body = "body".getBytes)) { result =>
        result shouldBe WithHeaders("Allow" -> "GET, HEAD") {
          Halt(405)
        }
      }
    }

    it("blows up on no match") {
      whenReady(routes.get("/nomatch")) { result =>
        result shouldBe Halt(404)
      }
    }

    it("bug - future of not http response") {
      whenReady(routes.get("/nomatch")) { result =>
        result shouldBe Halt(404)
      }
    }
  }
}