package com.springer.samatra.testing.unit

import java.io.{ByteArrayOutputStream, PrintStream}
import java.time.Clock
import java.util.concurrent.{CopyOnWriteArrayList, CountDownLatch, TimeUnit}

import com.springer.samatra.routing.FutureResponses.FutureHttpResp
import com.springer.samatra.routing.Request
import com.springer.samatra.routing.Routings._
import com.springer.samatra.routing.StandardResponses.{Halt, WithHeaders}
import com.springer.samatra.testing.servlet.ServletApiHelpers._
import javax.servlet.AsyncListener
import javax.servlet.http._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

object ControllerTestHelpers {

  implicit val clock: Clock = Clock.systemUTC()

  def unwrapFutureResp(resp: HttpResp)(implicit ex: ExecutionContext): HttpResp =
    if (!resp.isInstanceOf[FutureHttpResp[_]]) resp else {
      val fres = resp.asInstanceOf[FutureHttpResp[Any]]
      Await.result(fres.fut.map(fres.rest), Duration(fres.timeout, TimeUnit.MILLISECONDS))
    }

  implicit class HttpRespToProcessed(resp: HttpResp) {

    def run(): (Int, Map[String, Seq[String]], Seq[Cookie], Array[Byte]) = {
      val listeners = new CopyOnWriteArrayList[AsyncListener]()
      val latch = new CountDownLatch(1)
      val ignoredRequest = httpServletRequest("http", "", "", Map.empty, None, Seq.empty, asyncListeners = listeners, countDown = latch)
      val (status, headers, cookies, bytes) = InMemoryHttpReqResp(ignoredRequest, resp.process, latch, listeners)

      val maybeException = Option(ignoredRequest.getAttribute("javax.servlet.error.exception")).map { t =>
        val out = new ByteArrayOutputStream()
        t.asInstanceOf[Throwable].printStackTrace(new PrintStream(out))
        out.toByteArray
      }

      (status, headers, cookies, maybeException.getOrElse(bytes))
    }
  }

  implicit class ControllerTests(r: Routes)(implicit clock: Clock) {

    def get(path: String, headers: Map[String, Seq[String]] = Map.empty, cookies: Seq[Cookie] = Seq.empty): HttpResp =
      runRequest(r, httpServletRequest("http", path, "GET", headers, None, cookies, new CountDownLatch(0)))

    def head(path: String, headers: Map[String, Seq[String]] = Map.empty, cookies: Seq[Cookie] = Seq.empty): HttpResp =
      runRequest(r, httpServletRequest("http", path, "HEAD", headers, None, cookies, new CountDownLatch(0)))

    def post(path: String, headers: Map[String, Seq[String]] = Map.empty, body: Array[Byte], cookies: Seq[Cookie] = Seq.empty): HttpResp =
      runRequest(r, httpServletRequest("http", path, "POST", headers, Some(body), cookies, new CountDownLatch(0)))

    def put(path: String, headers: Map[String, Seq[String]] = Map.empty, body: Array[Byte], cookies: Seq[Cookie] = Seq.empty): HttpResp =
      runRequest(r, httpServletRequest("http", path, "PUT", headers, Some(body), cookies, new CountDownLatch(0)))

    def delete(path: String, headers: Map[String, Seq[String]] = Map.empty, cookies: Seq[Cookie] = Seq.empty): HttpResp =
      runRequest(r, httpServletRequest("http", path, "DELETE", headers, None, cookies, new CountDownLatch(0)))

    private def runRequest(r: Routes, httpReq: HttpServletRequest): HttpResp = {
      val request = Request(httpReq, started = clock.instant().toEpochMilli)

      r.matching(httpReq, null) match {
        case Right(route) =>
          val requestToResp: (Request) => HttpResp = route match {
            case PathParamsRoute(_, _, resp) => resp
            case RegexRoute(_, _, resp) => resp
          }
          val matches = route.matches(request)
          requestToResp(request.copy(params = matches.get))

        case Left(matchingRoutes: Seq[Route]) => matchingRoutes match {
          case Nil => Halt(404)
          case _ =>
            WithHeaders("Allow" -> Set(matchingRoutes.map(_.method): _*).mkString(", ")) {
              Halt(405)
            }
        }
      }
    }
  }
}
