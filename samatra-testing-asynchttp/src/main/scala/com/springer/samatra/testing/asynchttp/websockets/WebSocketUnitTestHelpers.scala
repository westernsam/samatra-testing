package com.springer.samatra.testing.asynchttp.websockets

import java.security.Principal
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{CountDownLatch, TimeUnit}

import com.springer.samatra.testing.servlet.InMemHttpServletRequest
import com.springer.samatra.websockets.WsRoutings.{SamatraWebSocket, WSController}
import io.netty.channel.ChannelFuture
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.DefaultHttpHeaders
import io.netty.handler.codec.http.websocketx.{BinaryWebSocketFrame, TextWebSocketFrame}
import org.asynchttpclient.netty.ws.NettyWebSocket
import org.asynchttpclient.ws.{WebSocket, WebSocketListener}
import org.scalatest.Matchers._

import scala.collection.mutable

object WebSocketUnitTestHelpers {
  implicit class WebSocketTextFrameTestingOps(ws: WSController) {

    private def uriMatch(path: String, candidate: String): Option[Map[String, String]] = {
      val actual: Array[String] = path.split("/")
      val pattern: Array[String] = candidate.split("/")

      if (actual.length != pattern.length)
        None
      else {
        val res: mutable.Map[String, String] = mutable.Map()

        for ((left, right) <- pattern.zip(actual)) {
          if (!left.equals(right))
            if (left.startsWith(":"))
              res.put(left.substring(1, left.length), right)
            else
              return None
        }

        Some(res.toMap)
      }
    }

    def on(path: String, blk: WebSocket => Any, upgradeReqHeaders: Map[String, Seq[String]] = Map.empty, user: Option[Principal] = None)(expect: String => Unit): WebSocketTextFrameTestingOps = {

      ws.routes.find(p => uriMatch(path, p.path).isDefined) match {
        case None => fail("no path found")
        case Some(r) =>
          val local = new LocalEndOfWebSocket(new SamatraWebSocket(r.socket, r.path))

          val channel = new EmbeddedChannel() {
            override def writeAndFlush(msg: scala.Any): ChannelFuture = {
              msg match {
                case text: TextWebSocketFrame => local.onMessage(text.text())
                case bin: BinaryWebSocketFrame => local.onMessage(bin.content.array())
              }
              super.writeAndFlush(msg)
            }
          }

          val nettyWebSocket = new NettyWebSocket(channel, new DefaultHttpHeaders())
          val session = new InMemWsSession(nettyWebSocket, new InMemHttpServletRequest("ws", r.path, "GET", headers = upgradeReqHeaders, body = None, cookies = Seq.empty), uriMatch(path, r.path).get, user)

          val latch = new CountDownLatch(1)
          val ref = new AtomicReference[String]()

          nettyWebSocket.addWebSocketListener(new WebSocketListener {
            override def onOpen(websocket: WebSocket): Unit = ()
            override def onClose(websocket: WebSocket, code: Int, reason: String): Unit = ()
            override def onError(t: Throwable): Unit = {
              latch.countDown()
              ref.set(t.getMessage)
            }
            override def onTextFrame(payload: String, finalFragment: Boolean, rsv: Int): Unit = {
              ref.set(payload)
              latch.countDown()
            }
          })
          local.onOpen(session)

          blk(nettyWebSocket)

          val bool = latch.await(1, TimeUnit.SECONDS)
          if (!bool) fail("No events received in 1 second")
          else {
            expect(ref.get())
            this
          }
      }
    }
  }
}
