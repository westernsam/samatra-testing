package com.springer.samatra.testing.asynchttp

import java.util.concurrent.{CountDownLatch, TimeUnit}

import com.springer.samatra.websockets.WsRoutings.{WSController, WsRoutes}
import org.asynchttpclient.AsyncHttpClient
import org.asynchttpclient.ws.{DefaultWebSocketListener, WebSocket, WebSocketUpgradeHandler}
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
        override def onMessage(message: String): Unit = {
          message shouldBe "> sam: boo"
          latch.countDown()
        }
      }).build()).get()

    socket.sendMessage("boo")
    if (!latch.await(1, TimeUnit.SECONDS)) fail("Expected websocket to close")
    socket.close()
  }

  it("should do ping/pong") {
    val latch = new CountDownLatch(5)

    lazy val socket: WebSocket = http.prepareGet(s"ws:/ping")
      .execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(new DefaultWebSocketListener() {
        override def onMessage(message: String): Unit = {
          message shouldBe "pong"
          if (latch.getCount > 0) {
            latch.countDown()
            socket.sendMessage("ping")
          }
        }
      }).build()).get()

    socket.sendMessage("ping")
    if (!latch.await(1, TimeUnit.SECONDS)) fail("Expected websocket to close")
    socket.close()
  }
}
