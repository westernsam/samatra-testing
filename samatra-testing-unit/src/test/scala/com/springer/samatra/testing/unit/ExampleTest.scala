package com.springer.samatra.testing.unit

import cats.effect.IO

import java.net.URLEncoder.encode
import java.security.Principal
import java.time.{Clock, LocalDate, ZoneId, ZoneOffset}
import java.util.concurrent.Executors
import com.springer.samatra.routing.Routings.{Controller, HttpResp, Routes}
import com.springer.samatra.routing.StandardResponses._
import com.springer.samatra.testing.unit.ControllerTestHelpers._
import com.springer.samatra.extras.cats.IoResponses.IoResponse

import javax.servlet.http.{Cookie, HttpServletRequest, HttpServletResponse}
import org.scalatest.FunSpec
import org.scalatest.Matchers._
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.{ExecutionContext, Future}

class ExampleTest extends FunSpec with ScalaFutures {
  implicit val ex: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5))

  val routes: Routes = new Controller {

    import com.springer.samatra.routing.FutureResponses.Implicits.fromFuture
    import com.springer.samatra.routing.StandardResponses.Implicits.fromString

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

    get("/request-response") { _ =>
      (req: HttpServletRequest, resp: HttpServletResponse) => {
        resp.setDateHeader("Date", LocalDate.of(2017, 5, 18).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli)
        resp.setStatus(200)
        resp.getWriter.print("sam")
      }
    }

    get("/futurebug") { _ =>
      Future {
        "hello"
      }
    }

    get("/hello-io") { _ =>
      IO {
        "hello io"
      }
    }

    get("/user") { req =>
      req.underlying.getUserPrincipal.getName
    }

    get("/hello/:name") { req =>
      Future {
        WithCookies(AddCookie("cookie", req.cookie("cookie").get)) {
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
      val result = routes.put(
        "/put",
        body = encode("to=/xml/hi", "UTF-8").getBytes,
        headers = Map("Content-Type" -> Seq("application/x-www-form-urlencoded")))

      val (statusCode, hs, _, _) = result.run()
      statusCode shouldBe 302
      hs("Location") shouldBe Seq("/xml/hi")
    }

    it("should map http resp inside future") {
      val result = routes.get("/futurebug")
      val (_, _, _, bytes) = result.run()
      new String(bytes) shouldBe "hello"
    }

    it("should get io string") {
      val result = routes.get("/hello-io")
      val (_, _, _, bytes) = result.run()
      new String(bytes) shouldBe "hello io"
    }

    it("should test with helper methods") {
      val result = routes.get("/request-response")
      val (statusCode, headers, _, body) = result.run()

      statusCode shouldBe 200
      headers("Date") shouldBe Seq("Thu, 18 05 2017 12:00:00 GMT")
      new String(body) shouldBe "sam"
    }

    it("set user principle") {
      val principal = new Principal {
        override def getName: String = "sam"
      }

      val (_, _, _, body) = routes.get("/user", userPrincipal = Some(principal)).run()
      new String(body) shouldBe "sam"
    }

    it("should test future string") {
      val result: HttpResp = unwrapFutureResp(routes.get("/hello/sam", cookies = Seq(new Cookie("cookie", "expectedValue"))))
      result shouldBe
        WithCookies(AddCookie("cookie", "expectedValue")) {
          WithHeaders("a" -> "b") {
            StringResp("sam")
          }
        }
    }

    it("returns 404 on no match") {
      routes.get("/nomatch") shouldBe Halt(404)
    }

    it("writes errors to output stream") {
      val (statusCode, _, _, body) = routes.get("/error").run()

      statusCode shouldBe 500
      new String(body) should include("Error message")
    }

    it("returns 405 on on MethodNotAllowed") {
      val result = routes.put("/hello/sam", body = "body".getBytes)
      result shouldBe WithHeaders("Allow" -> "GET, HEAD") {
        Halt(405)
      }
    }

    it("blows up on no match") {
      routes.get("/nomatch") shouldBe Halt(404)
    }

    it("bug - future of not http response") {
      routes.get("/nomatch") shouldBe Halt(404)
    }
  }

}