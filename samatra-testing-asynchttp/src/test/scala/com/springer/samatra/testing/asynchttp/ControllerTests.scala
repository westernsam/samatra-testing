package com.springer.samatra.testing.asynchttp

import java.util
import javax.servlet.{FilterConfig, ServletContext}

import com.springer.samatra.routing.Routings.Routes
import org.asynchttpclient.AsyncHttpClient
import org.eclipse.jetty.server.handler.ContextHandler.NoContext
import org.eclipse.jetty.servlets.GzipFilter
import org.scalatest.Matchers._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FunSpec}

import scala.collection.JavaConverters._

class ControllerTests extends FunSpec with ScalaFutures with RoutesFixtures with BeforeAndAfterAll with InMemoryBackend {

  val http: AsyncHttpClient = client(new ServerConfig {
    mount("/*", new GzipFilter() { //just an example - don't use for real - broken with ETag's
      init(new FilterConfig {
        override def getServletContext: ServletContext = new NoContext()
        override def getInitParameterNames: util.Enumeration[String] = null
        override def getFilterName: String = ""
        override def getInitParameter(name: String): String = name match {
          case "minGzipSize" => "0"
          case _ => null
        }
      })
    })

    mount("/*", Routes(basic))
    mount("/regex/*", Routes(regex))
    mount("/caching/*", Routes(caching))
    mount("/future/*", Routes(futures))
  })

  override protected def afterAll(): Unit = http.close()

  describe("Caching") {

    it("Adds no-store for noStore strategy") {
      val noStore = http.prepareGet("/caching/no-store").execute().get
      noStore.getHeader("Cache-Control") shouldBe "no-store"
    }

    it("Adds visibility and max-age headers for noRevalidate strategy") {
      val noRevalidate = http.prepareGet("/caching/no-revalidate").execute().get
      noRevalidate.getHeader("Cache-Control") shouldBe "public, max-age=600"
      noRevalidate.getHeader("Expires") should not be null
    }

    it("Adds Weak ETag support") {
      val res = http.prepareGet("/caching/weakEtag/sam").execute().get
      res.getStatusCode shouldBe 200
      res.getHeader("Cache-Control") shouldBe "no-cache, private"
      val etag: String = res.getHeader("ETag")
      etag should startWith("W/\"")

      val res2 = http.prepareGet(s"/caching/weakEtag/andy").setHeaders(Map("If-None-Match" -> Seq(etag))).execute().get
      res2.getStatusCode shouldBe 200

      val res3 = http.prepareGet(s"/caching/weakEtag/sam").setHeaders(Map("If-None-Match" -> Seq(etag))).execute().get
      res3.getStatusCode shouldBe 304
    }

    it("Adds Strong ETag support") {
      val res = http.prepareGet("/caching/etag/sam").execute().get
      res.getStatusCode shouldBe 200
      res.getHeader("Cache-Control") shouldBe "no-cache, private"
      val etag: String = res.getHeader("ETag")
      etag should not be null

      val res2 = http.prepareGet(s"/caching/etag/andy").setHeaders(Map("If-None-Match" -> Seq(etag))).execute().get
      res2.getStatusCode shouldBe 200

      val res3 = http.prepareGet(s"/caching/etag/sam").setHeaders(Map("If-None-Match" -> Seq(etag))).execute().get
      res3.getStatusCode shouldBe 304
    }
  }

  describe("Routes") {
    it("should return 404 for not found route") {
      val res = http.prepareGet("/querystringmap?1=a&1=b&2=c&3=%2623").execute().get()
      res.getResponseBody shouldBe "1->ab|2->c|3->&23"
    }

    it("should give query string map") {
      val res = http.prepareGet("/missing").execute().get
      res.getStatusCode shouldBe 404
    }

    it("should return unicode") {
      val res = http.prepareGet("/unicode").execute().get
      res.getResponseBody shouldBe "Почему это не рабосаетdafafdafdadfadfadf"
    }

    it("should return headers only for HEAD") {
      val res = http.prepareHead("/missing").execute().get
      res.getStatusCode shouldBe 404

      val resp = http.prepareHead("/head").execute().get
      resp.getStatusCode shouldBe 200
      resp.getHeader("header") shouldBe "value"
      //        resp.getHeader("Content-Length") shouldBe "0"

      val resp2 = http.prepareHead("/future/morethanone/string").execute().get
      resp2.getStatusCode shouldBe 200
      resp2.getHeader("Content-Length") shouldBe "6"
    }

    it("should return 405 for invalid method") {
      val wrong = http.preparePost("/himynameis/Sam").execute().get
      wrong.getStatusCode shouldBe 405
      wrong.getHeader("Allow") shouldBe "GET, HEAD"

      val missing = http.preparePost("/missing").execute().get
      missing.getStatusCode shouldBe 404

      val post = http.preparePost("/post").setBody("body").execute().get
      post.getResponseBody shouldBe "body"
    }

    it("HEAD should return 200, 302, 404 and 500 error codes") {
      val error = http.prepareHead("/future/morethanone/Error").execute().get
      error.getStatusCode shouldBe 500

      val notFound = http.prepareHead("/future/morethanone/NotFound").execute().get
      notFound.getStatusCode shouldBe 404

      val redirect = http.prepareHead("/future/morethanone/redirect").execute().get
      redirect.getStatusCode shouldBe 302

      val string = http.prepareHead("/future/morethanone/string").execute().get
      string.getStatusCode shouldBe 200

      val headers = http.prepareHead("/future/morethanone/headers").execute().get
      headers.getHeader("hi") shouldBe "there"

      val cs = http.prepareHead("/future/morethanone/cookies").execute().get
      val cookie = cs.getCookies.asScala.collectFirst {
        case c if c.getName == "cookie" => c.getValue
      }
      cookie shouldBe Some("tasty")
    }
  }

  it("should return 500 for timeout") {
    val res = http.prepareGet("/future/timeout").execute().get
    res.getStatusCode shouldBe 500
    res.getResponseBody should include ("java.util.concurrent.TimeoutException")
  }

  it("should parse path params") {
    val res = http.prepareGet("/himynameis/Sam").execute().get
    res.getResponseBody shouldBe "hi Sam"
  }

  it("should not URL decode parsed path params") {
    val res = http.prepareGet("/himynameis/Sam%2FOwen").execute().get
    res.getResponseBody shouldBe "hi Sam%2FOwen"
  }

  it("should parse regex params") {
    val res = http.prepareGet("/regex/year/2000").execute().get
    res.getResponseBody shouldBe "hell0 the year 2000"
  }

  it("should not URL decode parsed regex params") {
    val res = http.prepareGet("/regex/date/01%2F01%2F2000").execute().get
    res.getResponseBody shouldBe "hell0 the date 01%2F01%2F2000"
  }

  it("should be able to get and post from same uri") {
    val get = http.prepareGet("/getandpost").execute().get
    get.getResponseBody shouldBe "get"

    val post = http.preparePost("/getandpost").execute().get
    post.getResponseBody shouldBe "post"
  }

  it("should be able to set cookies") {
    val res = http.prepareGet("/future/morethanone/cookies").execute().get
    val head = res.getCookies.asScala.head
    head.getValue shouldBe "tasty"
    head.isHttpOnly shouldBe true
  }

  it("pathInfo should not include servlet path") {
    val res = http.prepareGet("/future/morethanone/pathInfo").execute().get
    res.getResponseBody shouldBe "/morethanone/pathInfo"
  }


  it("should be able to retrieve request uri") {
    val res = http.prepareGet("/uri?foo=bar#qunx").execute().get
    res.getResponseBody should endWith("/uri?foo=bar")
  }

  it("should be able to use GzipHandler") {
    def shouldBeGzipped(path: String): Unit = {
      val res = http.prepareGet(s"$path").setHeaders(Map("Accept-Encoding" -> Seq("gzip"))).execute().get
      res.getHeader("Content-Encoding") shouldBe "gzip"
    }

    shouldBeGzipped("/file")
    shouldBeGzipped("/future/morethanone/file")
    shouldBeGzipped("/caching/etag/andy")
  }
}
