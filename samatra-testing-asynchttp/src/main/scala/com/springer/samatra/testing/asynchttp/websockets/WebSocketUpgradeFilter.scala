
package com.springer.samatra.testing.asynchttp.websockets

import java.io.{OutputStream, Writer}
import java.net.URI
import java.nio.ByteBuffer
import java.security.Principal
import java.util
import java.util.Collections
import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import javax.websocket._
import javax.websocket.server.{ServerEndpoint, ServerEndpointConfig}

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFuture
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.websocketx._
import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaders}
import org.asynchttpclient.netty.ws.NettyWebSocket
import org.asynchttpclient.ws._

import scala.collection.JavaConverters._
import scala.collection.mutable

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

    if (actual.length != pattern.length)
      None
    else {
      val res: mutable.Map[String, String] = mutable.Map()

      for ((left, right) <- pattern.zip(actual)) {
        if (!left.equals(right))
          if (left.startsWith("{"))
            res.put(left.substring(1, left.length - 1), right)
          else
            return None
      }

      Some(res.toMap)
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

class InMemWsSession(remote: NettyWebSocket, req: HttpServletRequest, pathParams: Map[String, String], userPrincipal: Option[Principal]) extends Session {
  override def getUserPrincipal: Principal = userPrincipal.orNull
  override def setMaxIdleTimeout(milliseconds: Long): Unit = ???
  override def getUserProperties: util.Map[String, AnyRef] = {
    Map(
      "user" -> getUserPrincipal,
      "headers" -> Collections.list(req.getHeaderNames).asScala.map( k => k -> Collections.list(req.getHeaders(k))).asJava
    ).asJava
  }
  override def getId: String = "whatever"
  override def getBasicRemote: RemoteEndpoint.Basic = new RemoteEndpoint.Basic {
    override def sendBinary(data: ByteBuffer): Unit = remote.handleFrame(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(data)))
    override def sendText(text: String): Unit = remote.handleFrame(new TextWebSocketFrame(text))
    override def sendPing(applicationData: ByteBuffer): Unit = remote.handleFrame(new PingWebSocketFrame())
    override def sendPong(applicationData: ByteBuffer): Unit = remote.handleFrame(new PongWebSocketFrame())

    override def getSendWriter: Writer = ???
    override def sendBinary(partialByte: ByteBuffer, isLast: Boolean): Unit = ???
    override def sendText(partialMessage: String, isLast: Boolean): Unit = ???
    override def sendObject(data: scala.Any): Unit = ???
    override def getSendStream: OutputStream = ???
    override def getBatchingAllowed: Boolean = ???
    override def flushBatch(): Unit = ()
    override def setBatchingAllowed(allowed: Boolean): Unit = ()
  }
  override def getMaxBinaryMessageBufferSize: Int = ???
  override def getMaxTextMessageBufferSize: Int = ???
  override def getNegotiatedSubprotocol: String = ???
  override def getNegotiatedExtensions: util.List[Extension] = ???
  override def close(closeReason: CloseReason): Unit = remote.onClose(closeReason.getCloseCode.getCode, closeReason.getReasonPhrase)
  override def close(): Unit = this.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, ""))
  override def getAsyncRemote: RemoteEndpoint.Async = ???
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