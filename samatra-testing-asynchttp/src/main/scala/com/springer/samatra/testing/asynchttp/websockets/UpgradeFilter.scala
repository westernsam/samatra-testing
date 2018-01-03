
package com.springer.samatra.testing.asynchttp.websockets

import java.io.{OutputStream, Writer}
import java.net.{SocketAddress, URI}
import java.nio.ByteBuffer
import java.security.Principal
import java.util
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import javax.websocket._
import javax.websocket.server.{ServerEndpoint, ServerEndpointConfig}

import io.netty.channel.local.LocalAddress
import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaders}
import org.asynchttpclient.ws._

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class UpgradeFilter(websockets: Seq[ServerEndpointConfig], upgradeHandler: UpgradeHandler[WebSocket]) extends Filter {
  override def init(filterConfig: FilterConfig): Unit = ()
  override def destroy(): Unit = ()
  override def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain): Unit = {
    val req = request.asInstanceOf[HttpServletRequest]
    val path = URI.create(req.getRequestURL.toString).getPath
    val resp = response.asInstanceOf[HttpServletResponse]

    //todo: url template match
    val uriMatcher = uriMatch(path) _

    websockets.find(uriMatcher(_).isDefined) match {
      case Some(c) if req.getProtocol == "ws" =>
        //todo: modify handshake
        //        c.getConfigurator.modifyHandshake(c, new HandshakeRequest {
        //          override def getUserPrincipal: Principal = ???
        //          override def getHeaders: util.Map[String, util.List[String]] = ???
        //          override def getRequestURI: URI = ???
        //          override def getQueryString: String = ???
        //          override def getParameterMap: util.Map[String, util.List[String]] = ???
        //          override def isUserInRole(role: String): Boolean = ???
        //          override def getHttpSession: AnyRef = ???
        //        }, new HandshakeResponse {
        //          override def getHeaders: util.Map[String, util.List[String]] = ???
        //        })

        val local = new LocalEndOfWebSocket(c.getConfigurator.getEndpointInstance(c.getEndpointClass))
        val remote = new RemoteEndOfWebSocket(req, local)
        val session = new InMemWsSession(remote, req, uriMatcher(c).get)

        upgradeHandler.onSuccess(remote)
        local.onOpen(session)
        resp.setStatus(101)

      case _ => chain.doFilter(req, response)
    }
  }

  def uriMatch(path:String)(serverEndpoint: ServerEndpointConfig) : Option[Map[String, String]] = {
    val actual: Array[String] = path.split("/")
    val pattern: Array[String] = serverEndpoint.getPath.split("/")

    if (actual.length != pattern.length)
      None
    else {
      val res: mutable.Map[String, String] = mutable.Map()

      for ((left, right) <- pattern.zip(actual)) {
        if (!left.equals(right))
          if (left.startsWith("{"))
            res.put(left.substring(1, left.length-1), right)
          else
            return None
      }

      Some(res.toMap)
    }
  }

}

trait AggregateListener extends WebSocketByteListener with WebSocketTextListener with WebSocketPingListener with WebSocketPongListener

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

class RemoteEndOfWebSocket(upgradeRequest: HttpServletRequest, local: LocalEndOfWebSocket) extends WebSocket {
  val listeners: ArrayBuffer[WebSocketListener] = new ArrayBuffer[WebSocketListener]()

  def listener: AggregateListener = new AggregateListener() {
    override def onMessage(message: String): Unit = listeners.collect { case w: WebSocketTextListener => w.onMessage(message) }
    override def onMessage(message: Array[Byte]): Unit = listeners.collect { case w: WebSocketByteListener => w.onMessage(message) }
    override def onPong(message: Array[Byte]): Unit = listeners.collect { case w: WebSocketPongListener => w.onPong(message) }
    override def onPing(message: Array[Byte]): Unit = listeners.collect { case w: WebSocketPingListener => w.onPing(message) }
    override def onError(t: Throwable): Unit = listeners.foreach(_.onError(t))
    override def onClose(websocket: WebSocket): Unit = listeners.foreach(_.onClose(websocket))
    override def onOpen(websocket: WebSocket): Unit = listeners.foreach(_.onOpen(websocket))
  }

  val closed: AtomicBoolean = new AtomicBoolean(false)

  override def removeWebSocketListener(l: WebSocketListener): WebSocket = {
    listeners -= l
    this
  }
  override def addWebSocketListener(l: WebSocketListener): WebSocket = {
    listeners.append(l)
    this
  }

  override def getLocalAddress: SocketAddress = new LocalAddress("samatra-websocket-server")
  override def getRemoteAddress: SocketAddress = new LocalAddress("samatra-websocket-client")

  override def sendPong(payload: Array[Byte]): WebSocket = {
    local.onMessage(payload)
    this
  }
  override def sendPing(payload: Array[Byte]): WebSocket = {
    local.onMessage(payload)
    this
  }
  override def sendMessage(message: Array[Byte]): WebSocket = {
    local.onMessage(message)
    this
  }
  override def sendMessage(message: String): WebSocket = {
    local.onMessage(message)
    this
  }

  override def isOpen: Boolean = !closed.get()
  override def close(): Unit = {
    listeners.foreach(_.onClose(this))
    closed.set(true)
  }

  override def getUpgradeHeaders: HttpHeaders = {
    val headers = new DefaultHttpHeaders()
    val names = Collections.list(upgradeRequest.getHeaderNames).asScala

    names.foreach { name =>
      headers.add(name, upgradeRequest.getHeaders(name))
    }

    headers
  }

  override def stream(fragment: Array[Byte], last: Boolean): WebSocket = ???
  override def stream(fragment: Array[Byte], offset: Int, len: Int, last: Boolean): WebSocket = ???
  override def stream(fragment: String, last: Boolean): WebSocket = ???
}

class InMemWsSession(remote: RemoteEndOfWebSocket, req: HttpServletRequest, pathParams: Map[String, String]) extends Session {
  override def getUserPrincipal: Principal = ???
  override def setMaxIdleTimeout(milliseconds: Long): Unit = ???
  override def getUserProperties: util.Map[String, AnyRef] = ???
  override def getId: String = "whatever"
  override def getBasicRemote: RemoteEndpoint.Basic = new RemoteEndpoint.Basic {
    override def sendBinary(data: ByteBuffer): Unit = remote.listener.onMessage(data.array())
    override def sendText(text: String): Unit = remote.listener.onMessage(text)
    override def sendPing(applicationData: ByteBuffer): Unit = remote.listener.onPing(applicationData.array())
    override def sendPong(applicationData: ByteBuffer): Unit = remote.listener.onPong(applicationData.array())

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
  override def close(): Unit = remote.close()
  override def close(closeReason: CloseReason): Unit = remote.close()
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