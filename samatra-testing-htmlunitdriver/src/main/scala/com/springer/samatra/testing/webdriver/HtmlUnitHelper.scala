package com.springer.samatra.testing.webdriver

import java.net.URL

import com.gargoylesoftware.htmlunit.HttpMethod._
import com.gargoylesoftware.htmlunit._
import org.asynchttpclient.AsyncHttpClient
import org.openqa.selenium.WebDriver
import org.openqa.selenium.htmlunit.HtmlUnitDriver

import scala.collection.JavaConverters._


object HtmlUnitHelper {
  implicit class SamatraHtmlDriverWrapper(http: AsyncHttpClient) {
    def driver: WebDriver = new SamatraHtmlDriver(http)
  }

  class AsyncHttpWebConnection(http: AsyncHttpClient) extends WebConnection {
    def close(): Unit = ()

    override def getResponse(request: WebRequest): WebResponse = {
      val uri = request.getUrl.toURI
      val path = uri.getPath
      val qs = Option(uri.getRawQuery)

      val pathAndQuery = s"$path${qs.map(q => s"?$q").getOrElse("")}"

      val httpReq = request.getHttpMethod match {
        case HEAD => http.prepareHead(pathAndQuery).setFollowRedirect(true)
        case PATCH => http.preparePatch(pathAndQuery).setFollowRedirect(true)
        case TRACE => http.prepareTrace(pathAndQuery).setFollowRedirect(true)
        case GET => http.prepareGet(pathAndQuery).setFollowRedirect(true)
        case POST => http.preparePost(pathAndQuery).setBody(request.getRequestBody).setFollowRedirect(true)
        case PUT => http.preparePut(pathAndQuery).setBody(request.getRequestBody).setFollowRedirect(true)
        case DELETE => http.prepareDelete(pathAndQuery).setFollowRedirect(true)
        case OPTIONS => http.prepareOptions(pathAndQuery).setFollowRedirect(true)
      }

      request.getAdditionalHeaders.asScala.foreach {
        case (n, v) => httpReq.setHeader(n, v)
      }

      new StringWebResponse(httpReq.execute().get().getResponseBody, request.getUrl)
    }
  }

  class SamatraHtmlDriver(http: AsyncHttpClient) extends HtmlUnitDriver() {
    self =>

    override def get(url: String): Unit = if (WebClient.URL_ABOUT_BLANK.toString == url)
      super.get(url)
    else
      super.get(new URL(s"http://samatra-webdriver$url"))

    class RelativeUrlNav extends WebDriver.Navigation {
      override def forward(): Unit = SamatraHtmlDriver.super.navigate().forward()
      override def refresh(): Unit = SamatraHtmlDriver.super.navigate().refresh()
      override def back(): Unit = SamatraHtmlDriver.super.navigate().back()
      override def to(s: String): Unit = to(new URL(s"http://samatra-webdriver$s"))
      override def to(url: URL): Unit = SamatraHtmlDriver.super.navigate().to(url)
    }

    override def navigate(): WebDriver.Navigation = new RelativeUrlNav()

    override def modifyWebClient(client: WebClient): WebClient = {
      client.setWebConnection(new AsyncHttpWebConnection(http))
      client
    }
  }
}
