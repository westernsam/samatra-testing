package com.springer.samatra.testing.asynchttp

import java.util.concurrent.{CountDownLatch, TimeUnit}

import com.springer.samatra.websockets.WsRoutings.{WSController, WsRoutes}
import org.asynchttpclient.AsyncHttpClient
import org.asynchttpclient.ws.{WebSocket, WebSocketListener, WebSocketUpgradeHandler}
import org.scalatest.FunSpec
import org.scalatest.Matchers._
import org.scalatest.concurrent.ScalaFutures

class WebSocketsTest extends FunSpec with ScalaFutures with InMemoryBackend {

  val http: AsyncHttpClient = client(new ServerConfig {
    self =>
    WsRoutes(self, "/*", new WSController {
      mount("/ping") { ws =>
        (_: String) => {
          ws.send(s"pong")
        }
      }
      mount("/chat/:name") { ws =>
        (msg: String) => {
          ws.send(s"> ${ws.captured("name")}: $msg")
        }
      }
    })
  })

  it("should echo with captured stuff") {
    val latch = new CountDownLatch(1)

    val socket: WebSocket = http.prepareGet(s"ws:/chat/sam")
      .execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(new DefaultWebSocketListener() {
        override def onTextFrame(payload: String, finalFragment: Boolean, rsv: Int): Unit = {
          println(s"got $payload")
          payload shouldBe "> sam: boo"
          latch.countDown()
        }
      }).build()).get()

    socket.sendTextFrame("boo")
    if (!latch.await(1, TimeUnit.SECONDS)) fail("Expected websocket to close")
  }

  it("should do ping/pong") {
    val latch = new CountDownLatch(5)

    lazy val socket: WebSocket = http.prepareGet(s"ws:/ping")
      .execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(new DefaultWebSocketListener() {

        override def onTextFrame(payload: String, finalFragment: Boolean, rsv: Int): Unit = {
          payload shouldBe "pong"
          if (latch.getCount > 0) {
            latch.countDown()
            socket.sendTextFrame("ping")
          }
        }
      }).build()).get()

    socket.sendTextFrame("ping")
    if (!latch.await(1, TimeUnit.SECONDS)) fail("Expected websocket to close")
  }

  class DefaultWebSocketListener() extends WebSocketListener {
    override def onError(t: Throwable): Unit = t.printStackTrace()
    override def onClose(websocket: WebSocket, code: Int, reason: String): Unit = ()
    override def onOpen(websocket: WebSocket): Unit = ()
  }
}
