package com.springer.samatra.testing.asynchttp

import java.net.URI
import java.security.Principal
import java.util
import java.util.Collections

import com.springer.samatra.testing.servlet.InMemServletContext
import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaders}
import javax.servlet._
import javax.websocket.server.{ServerContainer, ServerEndpointConfig}
import javax.websocket.{ClientEndpointConfig, Endpoint, Extension, Session}
import org.asynchttpclient.AsyncHttpClient

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

trait Backend {

  implicit def toHttpHeaders(in: Map[String, Seq[String]]) : HttpHeaders = {
    val headers = new DefaultHttpHeaders

    in.foreach {
      case (k, v) => headers.add(k, v.asJava)
    }

    headers
  }

  def client(serverConfig: ServerConfig): AsyncHttpClient
  def clientAndBaseUrl(serverConfig: ServerConfig): (AsyncHttpClient, String)
}

class ServerConfig extends ServerContainer {
  self =>
  lazy val websockets: ArrayBuffer[ServerEndpointConfig] = new ArrayBuffer[ServerEndpointConfig]()
  lazy val filters: ArrayBuffer[(String, Filter)] = new ArrayBuffer[(String, Filter)]()
  lazy val routes: ArrayBuffer[(String, Servlet, ServletContext, Map[String, String], Option[Principal])] = new ArrayBuffer[(String, Servlet, ServletContext, Map[String, String], Option[Principal])]()

  def mount(path: String, f: Filter): Unit = filters.append(path -> f)
  def mount(path: String, s: Servlet): Unit = mount(path, s, None)
  def mount(path: String, s: Servlet, userPrincipal: Option[Principal] = None): Unit = mount(path, s, new InMemServletContext(s, path), Map.empty, userPrincipal)
  def mount(path: String, s: Servlet, context: ServletContext, initParams: Map[String, String], userPrincipal: Option[Principal]): Unit = routes.append((path, s, context, initParams, userPrincipal))

  def ++(other: ServerConfig): ServerConfig = new ServerConfig() {
    override lazy val websockets: ArrayBuffer[ServerEndpointConfig] = self.websockets ++ other.websockets
    override lazy val filters: ArrayBuffer[(String, Filter)] = self.filters ++ other.filters
    override lazy val routes: ArrayBuffer[(String, Servlet, ServletContext, Map[String, String], Option[Principal])] = self.routes ++ other.routes
  }

  override def addEndpoint(endpointClass: Class[_]): Unit = throw new UnsupportedOperationException("todo: use bloody annotations to call other addEndpoint thingy")
  override def addEndpoint(serverConfig: ServerEndpointConfig): Unit = websockets.append(serverConfig)

  override def setDefaultMaxTextMessageBufferSize(max: Int): Unit = ()
  override def connectToServer(annotatedEndpointInstance: scala.Any, path: URI): Session = throw new UnsupportedOperationException
  override def connectToServer(annotatedEndpointClass: Class[_], path: URI): Session = throw new UnsupportedOperationException
  override def connectToServer(endpointInstance: Endpoint, cec: ClientEndpointConfig, path: URI): Session = throw new UnsupportedOperationException
  override def connectToServer(endpointClass: Class[_ <: Endpoint], cec: ClientEndpointConfig, path: URI): Session = throw new UnsupportedOperationException
  override def getDefaultAsyncSendTimeout: Long = -1
  override def setDefaultMaxBinaryMessageBufferSize(max: Int): Unit = ()
  override def getDefaultMaxSessionIdleTimeout: Long = -1
  override def getDefaultMaxTextMessageBufferSize: Int = -1
  override def setAsyncSendTimeout(timeoutmillis: Long): Unit = ()
  override def setDefaultMaxSessionIdleTimeout(timeout: Long): Unit = ()
  override def getDefaultMaxBinaryMessageBufferSize: Int = -1
  override def getInstalledExtensions: util.Set[Extension] = Collections.emptySet()
}
