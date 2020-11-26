package com.springer.samatra.testing.webdriver

import com.springer.samatra.routing.Routings.{Controller, Routes}
import com.springer.samatra.routing.StandardResponses.Implicits.fromString
import com.springer.samatra.routing.StandardResponses.{AddCookie, Redirect, WithCookies}
import com.springer.samatra.testing.asynchttp.{InMemoryBackend, ServerConfig}
import com.springer.samatra.testing.webdriver.HtmlUnitHelper.SamatraHtmlDriverWrapper
import org.asynchttpclient.AsyncHttpClient
import org.openqa.selenium.{By, WebDriver}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers._

class HtmlUnitTest extends AnyFunSpec with InMemoryBackend {

  it("should be able to get a page") {
    val http: AsyncHttpClient = client(new ServerConfig {
      mount("/home/*", Routes(new Controller {
        get("/Redirect") { _ =>
          WithCookies(AddCookie("cookie", "hi", domain = None, path=None)) {
            Redirect("/home/Hello")
          }
        }
        get("/Hello") { req =>
          s"""<html>
             |<head>
             |  <title>${req.queryStringParamValue("title")}</title>
             |</head>
             |<h1 id="cookie">${req.cookie("cookie").getOrElse("")}</h1>
             |</html>""".stripMargin
        }
      }))
    })

    val driver: WebDriver = http.driver

    driver.get("/home/Redirect")
    driver.getCurrentUrl should endWith("/Hello")
    driver.findElement(By.id("cookie")).getText shouldBe "hi"
  }

}
