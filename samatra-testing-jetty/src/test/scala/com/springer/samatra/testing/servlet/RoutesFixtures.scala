package com.springer.samatra.testing.servlet

import java.nio.file.Paths
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import com.springer.samatra.routing.CacheStrategies
import com.springer.samatra.routing.CacheStrategies._
import com.springer.samatra.routing.FutureResponses.Implicits.fromFuture
import com.springer.samatra.routing.FutureResponses.fromFutureWithTimeout
import com.springer.samatra.routing.Routings._
import com.springer.samatra.routing.StandardResponses.Implicits._
import com.springer.samatra.routing.StandardResponses._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait RoutesFixtures {

  def futures: Routes = new Controller {
    //with implicits
    get("/morethanone/:type") { req =>
      Future[HttpResp] {
        req.captured("type") match {
          case "redirect" => Redirect("/getandpost")
          case "string" => "String"
          case "Error" => Halt(500)
          case "NotFound" => Halt(404)
          case "file" => WithHeaders("Content-Type" -> "application/xml") {
            Paths.get("build.sbt")
          }
          case "headers" => WithHeaders("hi" -> "there")("body")
          case "cookies" =>
            WithCookies(Seq(AddCookie("cookie", "tasty")))("body")
          case "securedcookies" =>
            WithCookies(Seq(AddCookie("cookie", "tasty", httpOnly = true)))("body")
        }
      }
    }

    //use explicits
    get("/timeout") { _ =>
      fromFutureWithTimeout(timeout = 1, Future {
        Thread.sleep(200)
        fromString("oh hello there - i didn't expect to see you")
      }, logThreadDumpOnTimeout = true)
    }
  }

  def caching: Routes = new Controller {
    get("/no-store") { _ => noStore("Should have Cache-Control: no-store header") }
    get("/no-revalidate") { _ => noRevalidate(Public, Some(600))("Should have Cache-Control: public, max-age=600 header") }
    get("/etag/:name") { req =>
      Future {
        revalidateWithStrongEtag(Private) {
          {
            s"Should have ETag for ${req.captured("name")}"
          }
        }
      }
    }
    get("/weakEtag/:name") { req =>
      Future {
        CacheStrategies.revalidate(Private, etagStrategy = { () => req.captured("name").hashCode.toString }) {
          {
            s"Should have ETag for ${req.captured("name")}"
          }
        }
      }
    }
  }

  def regex: Routes = new Controller {
    get("^/year/(\\d\\d\\d\\d)$".r) { req => s"hell0 the year ${req.captured(0)}" }
    get("^/date/(.*)$".r) { req => s"hell0 the date ${req.captured(0)}" }
  }

  def basic: Routes = new AggregateRoutes(
    new Controller {
      get("/himynameis/:name")(req => s"hi ${req.captured("name")}")
      get("/himyusernameis")(req => s"hi ${req.underlying.getUserPrincipal.getName}")
      get("/file") { _ =>
        WithHeaders("Content-Type" -> "application/xml") {
          Paths.get("build.sbt")
        }
      }
      get("/uri")(_.toUri)
      get("/unicode")(_ => {
        (req : HttpServletRequest, res: HttpServletResponse) => {
          res.setContentType("application/json; charset=utf-8")
          res.getOutputStream.write("Почему это не рабосаетdafafdafdadfadfadf".getBytes())
        }
      })
      get("/querystringmap") {
        _.queryStringMap.map { case (k, v) => s"$k->${v.mkString}" }.toSeq.sorted.mkString("|")
      }
    },
    new Controller {
      head("/head") { _ => HeadersOnly("header" -> "value") }
      post("/post") { req => req.bodyAsStream }
      get("/getandpost") { _ => "get" }
      post("/getandpost") { _ => "post" }
      get("^/regex/year/(\\d\\d\\d\\d)$".r) { _ => Halt(500, Some(new IllegalStateException("servlet path takes precedence"))) }
    })
}
