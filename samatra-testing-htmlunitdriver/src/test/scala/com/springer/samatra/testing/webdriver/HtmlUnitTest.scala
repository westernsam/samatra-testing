package com.springer.samatra.testing.webdriver

import com.springer.samatra.routing.Routings.{Controller, Routes}
import com.springer.samatra.testing.asynchttp.{InMemoryBackend, ServerConfig}
import com.springer.samatra.testing.webdriver.HtmlUnitHelper.SamatraHtmlDriverWrapper
import org.asynchttpclient.AsyncHttpClient
import org.openqa.selenium.WebDriver
import org.scalatest.FunSpec
import org.scalatest.Matchers._

import com.springer.samatra.routing.StandardResponses.Implicits.fromString

class HtmlUnitTest extends FunSpec with InMemoryBackend {

  it("should be able to get a page") {
    val http: AsyncHttpClient = client(new ServerConfig {
      mount("/home/*", Routes(new Controller {
        get("/Hello") { req =>
          s"""<html>
            |<head>
            |  <title>${req.queryStringParamValue("title")}</title>
            |</head>
            |</html>""".stripMargin
        }
      }))
    })

    val driver: WebDriver = http.driver

    driver.get("/home/Hello?title=Hi%20Sam")
    driver.getTitle shouldBe "Hi Sam"
  }

}
