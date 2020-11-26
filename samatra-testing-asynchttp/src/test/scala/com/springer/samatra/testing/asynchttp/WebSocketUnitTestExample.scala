package com.springer.samatra.testing.asynchttp

import java.util.concurrent.TimeUnit
import com.springer.samatra.websockets.WsRoutings.{WSController, WriteOnly}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers._

import scala.concurrent.duration.Duration

class WebSocketUnitTestExample extends AnyFunSpec {

  import com.springer.samatra.testing.asynchttp.websockets.WebSocketUnitTestHelpers._

  val controller: WSController = new WSController {
    mount("/ping") { ws => _ => ws.send("pong") }
    mount("/hello/:name") { ws => _ => ws.send(ws.captured("name")) }
    mount("/echo") { ws => msg => ws.send(msg) }
    mount("/connect") { ws =>
      new WriteOnly {
        override def onConnect(): Unit = ws.send("hiya")
      }
    }
  }

  it("examples") {
    controller.on("/ping", _.sendTextFrame("whateva")) {
      _ shouldBe "pong"
    }.on("/echo", _.sendTextFrame("echo")) {
      _ shouldBe "echo"
    }.on("/hello/sam", _.sendTextFrame("name")) {
      _ shouldBe "sam"
    }.on("/connect", _ => ()) {
      _ shouldBe "hiya"
    }
  }

  it("support never") {
    controller.noEventExpectedOn("/ping",  _ => (), timeout = Duration(100, TimeUnit.MILLISECONDS))
  }
}
