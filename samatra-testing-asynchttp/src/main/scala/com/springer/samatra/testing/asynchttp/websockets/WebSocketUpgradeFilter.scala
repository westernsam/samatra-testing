
package com.springer.samatra.testing.asynchttp.websockets

import java.io.{OutputStream, Writer}
import java.net.URI
import java.nio.ByteBuffer
import java.security.Principal
import java.util
import java.util.Collections
import java.util.concurrent.{Callable, Future, FutureTask}

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFuture
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.websocketx._
import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaders}
import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import javax.websocket._
import javax.websocket.server.{ServerEndpoint, ServerEndpointConfig}
import org.asynchttpclient.netty.ws.NettyWebSocket
import org.asynchttpclient.ws._

import scala.collection.JavaConverters._

class WebSocketUpgradeFilter(websockets: Seq[ServerEndpointConfig], upgradeHandler: WebSocketUpgradeHandler) extends Filter {

  def getUpgradeHeaders(upgradeRequest: HttpServletRequest): HttpHeaders = {
    val headers = new DefaultHttpHeaders()

    Collections.list(upgradeRequest.getHeaderNames).asScala.foreach { name =>
      headers.add(name, upgradeRequest.getHeaders(name))
    }

    headers
  }

  override def init(filterConfig: FilterConfig): Unit = ()
  override def destroy(): Unit = ()
  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
    val req = request.asInstanceOf[HttpServletRequest]
    val path = URI.create(req.getRequestURL.toString).getPath
    val resp = response.asInstanceOf[HttpServletResponse]

    val uriMatcher = uriMatch(path) _

    websockets.find(uriMatcher(_).isDefined) match {
      case Some(c) if req.getProtocol == "ws" =>
        val local = new LocalEndOfWebSocket(c.getConfigurator.getEndpointInstance(c.getEndpointClass))

        val channel = new EmbeddedChannel() {
          override def writeAndFlush(msg: scala.Any): ChannelFuture = {
            msg match {
              case text: TextWebSocketFrame => local.onMessage(text.text())
              case bin: BinaryWebSocketFrame => local.onMessage(bin.content.array())
              case _ => throw new UnsupportedOperationException("Unknown message type "  + msg)
            }
            super.writeAndFlush(msg)
          }
        }
        val remote = new NettyWebSocket(channel, getUpgradeHeaders(req))
        val session = new InMemWsSession(remote, req, uriMatcher(c).get, Option(req.getUserPrincipal))

        upgradeHandler.setWebSocket(remote)
        upgradeHandler.onOpen()
        local.onOpen(session)
        resp.setStatus(101)

      case _ => chain.doFilter(req, response)
    }
  }

  def uriMatch(path: String)(serverEndpoint: ServerEndpointConfig): Option[Map[String, String]] = {
    val actual: Array[String] = path.split("/")
    val pattern: Array[String] = serverEndpoint.getPath.split("/")

    if (actual.length != pattern.length) None
    else pattern.zip(actual).foldLeft[Option[Map[String, String]]](Some(Map.empty)) {
      case (r, (left, right)) => r match {
        case None => None
        case Some(map) =>
          if (left == right) r
          else if (left.startsWith("{")) Some(map + (left.substring(1, left.length - 1) -> right))
          else None
      }
    }
  }
}

class LocalEndOfWebSocket(ws: Any) {
  if (!ws.getClass.getAnnotations.exists(ann => classOf[ServerEndpoint].isAssignableFrom(ann.getClass)))
    throw new IllegalStateException(s"${ws.getClass.getAnnotations.toList} didn't contain ServerEndpoint")

  def onOpen(sess: Session): Unit = ws.getClass.getDeclaredMethods.find(_.getDeclaredAnnotations.exists(ann => classOf[OnOpen].isAssignableFrom(ann.getClass))) match {
    case Some(m) => m.invoke(ws, sess)
    case _ => throw new IllegalStateException(s"Found no @onOpen on ${ws.getClass.getSimpleName}")
  }

  def onMessage(message: String): Unit = ws.getClass.getDeclaredMethods.find(_.getDeclaredAnnotations.exists(ann => classOf[OnMessage].isAssignableFrom(ann.getClass))) match {
    case Some(m) => m.invoke(ws, message)
    case _ => throw new IllegalStateException(s"Found no @onOpen on ${ws.getClass.getSimpleName}")
  }

  def onMessage(message: Array[Byte]): Unit = ws.getClass.getDeclaredMethods.find(_.getDeclaredAnnotations.exists(ann => classOf[OnMessage].isAssignableFrom(ann.getClass))) match {
    case Some(m) => m.invoke(ws, message)
    case _ => throw new IllegalStateException(s"Found no @onOpen on ${ws.getClass.getSimpleName}")
  }

  def onClose(reason: CloseReason): Unit = ws.getClass.getDeclaredMethods.find(_.getDeclaredAnnotations.exists(ann => classOf[OnClose].isAssignableFrom(ann.getClass))) match {
    case Some(m) => m.invoke(ws, reason)
    case _ => throw new IllegalStateException(s"Found no @onOpen on ${ws.getClass.getSimpleName}")
  }

  def onError(cause: Throwable): Unit = ws.getClass.getDeclaredMethods.find(_.getDeclaredAnnotations.exists(ann => classOf[OnError].isAssignableFrom(ann.getClass))) match {
    case Some(m) => m.invoke(ws, cause)
    case _ => throw new IllegalStateException(s"Found no @onOpen on ${ws.getClass.getSimpleName}")
  }
}

class InMemRemoteEndpoint(remote: NettyWebSocket) extends RemoteEndpoint {
  override def sendPing(applicationData: ByteBuffer): Unit = remote.handleFrame(new PingWebSocketFrame())
  override def sendPong(applicationData: ByteBuffer): Unit = remote.handleFrame(new PongWebSocketFrame())
  override def getBatchingAllowed: Boolean = ???
  override def setBatchingAllowed(allowed: Boolean): Unit = ()
  override def flushBatch(): Unit = ()
}

class InMemWsSession(remote: NettyWebSocket, req: HttpServletRequest, pathParams: Map[String, String], userPrincipal: Option[Principal]) extends Session {
  override def getUserPrincipal: Principal = userPrincipal.orNull
  override def setMaxIdleTimeout(milliseconds: Long): Unit = ???
  override def getUserProperties: util.Map[String, AnyRef] = {
    val java1: util.List[(String, util.ArrayList[String])] = Collections.list(req.getHeaderNames).asScala.map(k => k -> Collections.list(req.getHeaders(k))).asJava

    val value: Map[String, Object] = Map(
      "user" -> getUserPrincipal,
      "headers" -> java1
    )

    val java = value.asJava

    java
  }
  override def getId: String = "whatever"
  override def getBasicRemote: RemoteEndpoint.Basic = new InMemRemoteEndpoint(remote) with RemoteEndpoint.Basic {
    override def sendText(partialMessage: String, isLast: Boolean): Unit = ???
    override def sendBinary(partialByte: ByteBuffer, isLast: Boolean): Unit = ???
    override def getSendStream: OutputStream = ???
    override def getSendWriter: Writer = ???
    override def sendObject(data: Any): Unit = ???

    def sendBinary(data: ByteBuffer): Unit = remote.handleFrame(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(data)))
    def sendText(text: String): Unit = remote.handleFrame(new TextWebSocketFrame(text))
  }
  override def getMaxBinaryMessageBufferSize: Int = ???
  override def getMaxTextMessageBufferSize: Int = ???
  override def getNegotiatedSubprotocol: String = ???
  override def getNegotiatedExtensions: util.List[Extension] = ???
  override def close(closeReason: CloseReason): Unit = remote.onClose(closeReason.getCloseCode.getCode, closeReason.getReasonPhrase)
  override def close(): Unit = this.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, ""))
  override def getAsyncRemote: RemoteEndpoint.Async =
  new InMemRemoteEndpoint(remote) with RemoteEndpoint.Async {
    override def getSendTimeout: Long = -1
    override def setSendTimeout(timeoutmillis: Long): Unit = ()
    override def sendText(text: String, handler: SendHandler): Unit = ???
    override def sendBinary(data: ByteBuffer, handler: SendHandler): Unit = ???
    override def sendObject(data: Any): Future[Void] = ???
    override def sendObject(data: Any, handler: SendHandler): Unit = ???

    override def sendText(text: String): Future[Void] =  {
      val value = new FutureTask[Void](new Callable[Void] {
        override def call(): Void = {
          remote.handleFrame(new TextWebSocketFrame(text))
          null
        }
      })
      value.run()
      value
    }
    override def sendBinary(data: ByteBuffer): Future[Void] = {
      val value = new FutureTask[Void](new Callable[Void] {
        override def call(): Void = {
          remote.handleFrame(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(data)))
          null
        }
      })
      value.run()
      value
    }
  }
  override def getRequestParameterMap: util.Map[String, util.List[String]] = ???
  override def removeMessageHandler(handler: MessageHandler): Unit = ???
  override def setMaxBinaryMessageBufferSize(length: Int): Unit = ???
  override def getRequestURI: URI = ???
  override def getContainer: WebSocketContainer = ???
  override def getQueryString: String = ???
  override def getMaxIdleTimeout: Long = ???
  override def isSecure: Boolean = false
  override def addMessageHandler(handler: MessageHandler): Unit = ???
  override def addMessageHandler[T](clazz: Class[T], handler: MessageHandler.Whole[T]): Unit = ???
  override def addMessageHandler[T](clazz: Class[T], handler: MessageHandler.Partial[T]): Unit = ???
  override def getProtocolVersion: String = ???
  override def isOpen: Boolean = true
  override def getPathParameters: util.Map[String, String] = pathParams.asJava
  override def setMaxTextMessageBufferSize(length: Int): Unit = ???
  override def getOpenSessions: util.Set[Session] = ???
  override def getMessageHandlers: util.Set[MessageHandler] = ???
}