package com.springer.samatra.testing.unit

import java.io.{ByteArrayOutputStream, PrintStream}
import java.time.Clock
import java.util.concurrent.CountDownLatch
import javax.servlet.http._

import com.springer.samatra.routing.FutureResponses.FutureHttpResp
import com.springer.samatra.routing.Request
import com.springer.samatra.routing.Routings._
import com.springer.samatra.routing.StandardResponses.{Halt, WithHeaders}
import com.springer.samatra.testing.servlet.ServletApiHelpers._

import scala.concurrent.{ExecutionContext, Future}

object ControllerTestHelpers {

  implicit val clock: Clock = Clock.systemUTC()

  implicit class HttpRespToProcessed(resp: HttpResp) {

    def run(): (Int, Map[String, Seq[String]], Seq[Cookie], Array[Byte]) = {
      val ignoredRequest = httpServletRequest("", "", Map.empty, None, Seq.empty)
      val (status, headers, cookies, bytes) = InMemoryHttpReqResp(ignoredRequest, resp.process)

      val maybeException = Option(ignoredRequest.getAttribute("javax.servlet.error.exception")).map { t =>
        val out = new ByteArrayOutputStream()
        t.asInstanceOf[Throwable].printStackTrace(new PrintStream(out))
        out.toByteArray
      }

      (status, headers, cookies, maybeException.getOrElse(bytes))
    }
  }

  implicit class ControllerTests(r: Routes)(implicit clock: Clock) {

    def get(path: String, headers: Map[String, Seq[String]] = Map.empty, cookies: Seq[Cookie] = Seq.empty)(implicit ex: ExecutionContext): Future[HttpResp] =
      runRequest(r, httpServletRequest(path, "GET", headers, None, cookies, new CountDownLatch(0)))

    def head(path: String, headers: Map[String, Seq[String]] = Map.empty, cookies: Seq[Cookie] = Seq.empty)(implicit ex: ExecutionContext): Future[HttpResp] =
      runRequest(r, httpServletRequest(path, "HEAD", headers, None, cookies, new CountDownLatch(0)))

    def post(path: String, headers: Map[String, Seq[String]] = Map.empty, body: Array[Byte], cookies: Seq[Cookie] = Seq.empty)(implicit ex: ExecutionContext): Future[HttpResp] =
      runRequest(r, httpServletRequest(path, "POST", headers, Some(body), cookies, new CountDownLatch(0)))

    def put(path: String, headers: Map[String, Seq[String]] = Map.empty, body: Array[Byte], cookies: Seq[Cookie] = Seq.empty)(implicit ex: ExecutionContext): Future[HttpResp] =
      runRequest(r, httpServletRequest(path, "PUT", headers, Some(body), cookies, new CountDownLatch(0)))

    def delete(path: String, headers: Map[String, Seq[String]] = Map.empty, cookies: Seq[Cookie] = Seq.empty)(implicit ex: ExecutionContext): Future[HttpResp] =
      runRequest(r, httpServletRequest(path, "DELETE", headers, None, cookies, new CountDownLatch(0)))

    private def runRequest(r: Routes, httpReq: HttpServletRequest)(implicit ex: ExecutionContext): Future[HttpResp] = {
      val request = Request(httpReq, started = clock.instant().toEpochMilli)

      futureFrom(r.matching(httpReq, null) match {
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
      })
    }

    private def futureFrom(resp: HttpResp)(implicit ex: ExecutionContext): Future[HttpResp] = {
      resp match {
        case FutureHttpResp(fut: Future[_], _, f : Function1[Any, HttpResp], _, _, _) => fut.map(f)
        case _ => Future.successful(resp)
      }
    }
  }
}
