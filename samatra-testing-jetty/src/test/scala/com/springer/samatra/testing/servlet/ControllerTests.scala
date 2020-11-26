package com.springer.samatra.testing.servlet

import java.security.Principal
import javax.servlet._
import javax.servlet.http.HttpServletResponse
import com.springer.samatra.routing.Routings.Routes
import com.springer.samatra.testing.asynchttp.{JettyBacked, ServerConfig}
import org.asynchttpclient.AsyncHttpClient
import org.scalatest.matchers.should.Matchers._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec

import scala.jdk.CollectionConverters.CollectionHasAsScala


class ControllerTests extends AnyFunSpec with ScalaFutures with RoutesFixtures with BeforeAndAfterAll with JettyBacked {

  val http: AsyncHttpClient = client(new ServerConfig {
    mount("/*", new Filter {
      override def init(filterConfig: FilterConfig): Unit = ()
      override def destroy(): Unit = ()
      override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
        response.asInstanceOf[HttpServletResponse].setHeader("X-Extra-Header", "extra")
        chain.doFilter(request, response)
      }
    }, userPrincipal = Some(new Principal() {
      override def getName: String = "Sam"
    }))

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
        case c if c.name == "cookie" => c.value()
      }
      cookie shouldBe Some("tasty")
    }
  }

  it("should return 500 for timeout") {
    val res = http.prepareGet("/future/timeout").execute().get
    res.getStatusCode shouldBe 500
    withClue(res.getResponseBody) {
      res.getResponseBody should include("java.util.concurrent.TimeoutException")
    }
  }

  it("should be able to gte user principle") {
    val res = http.prepareGet("/himyusernameis").execute().get
    res.getResponseBody shouldBe "hi Sam"
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
    res.getCookies.asScala.head.value shouldBe "tasty"
  }

  it("should be able to retrieve request uri") {
    val res = http.prepareGet("/uri?foo=bar#qunx").execute().get
    res.getResponseBody should endWith("/uri?foo=bar")
  }

  it("should be able to use Filter") {
    def withFilters(path: String): Unit = {
      val res = http.prepareGet(s"$path").execute().get
      res.getHeader("X-Extra-Header") shouldBe "extra"
    }

    withFilters("/file")
    withFilters("/future/morethanone/file")
    withFilters("/caching/etag/andy")
  }
}
