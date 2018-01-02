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
        (msg: String) => {
          println(s"Got $msg")
          ws.send(s"pong")
        }
      }
    })
  })

  val latch = new CountDownLatch(5)

  val socket: WebSocket = http.prepareGet(s"ws:/ping")
    .execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(new DefaultWebSocketListener() {
      override def onMessage(message: String): Unit = {
        println(s"Got $message")
        message shouldBe "pong"
        if (latch.getCount > 0) {
          latch.countDown()
          socket.sendMessage("ping")
        }
      }
    }).build()).get()

  it("should do web sockets") {
    socket.sendMessage("ping")
    if(!latch.await(1, TimeUnit.SECONDS)) fail("Expected websocket to close")
    socket.close()
  }
}
