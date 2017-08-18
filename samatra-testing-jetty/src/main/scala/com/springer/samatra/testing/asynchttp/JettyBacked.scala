package com.springer.samatra.testing.asynchttp

import java.util
import javax.servlet.DispatcherType

import com.springer.samatra.testing.servlet.InMemServletContext

import scala.collection.JavaConverters._
import org.asynchttpclient.{AsyncHandler, AsyncHttpClient, BoundRequestBuilder, DefaultAsyncHttpClient, DefaultAsyncHttpClientConfig, ListenableFuture, Request, RequestBuilder, Response, SignatureCalculator}
import org.eclipse.jetty.server.{Connector, Server, ServerConnector}
import org.eclipse.jetty.servlet.{FilterHolder, ServletContextHandler, ServletHolder}

trait JettyBacked extends Backend {

  override def client(serverConfig: ServerConfig): AsyncHttpClient = {
    val (underlying, host) = clientAndBaseUrl(serverConfig)

    new AsyncHttpClient {
      override def preparePatch(url: String): BoundRequestBuilder = underlying.preparePatch(s"$host$url")
      override def preparePost(url: String): BoundRequestBuilder = underlying.preparePost(s"$host$url")
      override def prepareGet(url: String): BoundRequestBuilder = underlying.prepareGet(s"$host$url")
      override def prepareDelete(url: String): BoundRequestBuilder = underlying.prepareDelete(s"$host$url")
      override def preparePut(url: String): BoundRequestBuilder = underlying.preparePut(s"$host$url")
      override def prepareHead(url: String): BoundRequestBuilder = underlying.prepareHead(s"$host$url")
      override def prepareOptions(url: String): BoundRequestBuilder = underlying.prepareOptions(s"$host$url")
      override def prepareTrace(url: String): BoundRequestBuilder = underlying.prepareTrace(s"$host$url")
      override def prepareConnect(url: String): BoundRequestBuilder = underlying.prepareConnect(s"$host$url")
      override def prepareRequest(request: Request): BoundRequestBuilder = underlying.prepareRequest(request)
      override def prepareRequest(requestBuilder: RequestBuilder): BoundRequestBuilder = underlying.prepareRequest(requestBuilder)

      override def setSignatureCalculator(signatureCalculator: SignatureCalculator): AsyncHttpClient = underlying.setSignatureCalculator(signatureCalculator)
      override def isClosed: Boolean = underlying.isClosed
      override def executeRequest[T](request: Request, handler: AsyncHandler[T]): ListenableFuture[T] = underlying.executeRequest(request, handler)
      override def executeRequest[T](requestBuilder: RequestBuilder, handler: AsyncHandler[T]): ListenableFuture[T] = underlying.executeRequest(requestBuilder, handler)
      override def executeRequest(request: Request): ListenableFuture[Response] = underlying.executeRequest(request)
      override def executeRequest(requestBuilder: RequestBuilder): ListenableFuture[Response] = underlying.executeRequest(requestBuilder)
      override def close(): Unit = underlying.close()
    }
  }

  override def clientAndBaseUrl(serverConfig: ServerConfig): (AsyncHttpClient, String) = {
    lazy val server = new Server() {
      addConnector(new ServerConnector(this) {
        setPort(0)
      })

      setHandler(new ServletContextHandler() {

        serverConfig.filters.foreach {
          case (contextPath, filter) =>
            addFilter(new FilterHolder(filter), contextPath, util.EnumSet.of(DispatcherType.REQUEST))
        }

        serverConfig.routes.foreach {
          case (contextPath, servlet, c, ip) =>

            c match {
              case sc: InMemServletContext => sc.attributes.asScala.foreach { case (n, v) => getServletContext.setAttribute(n, v) }
              case _ =>
            }

            val holder = new ServletHolder(servlet)
            holder.setInitParameters(ip.asJava)
            addServlet(holder, contextPath)
        }
      })
    }

    server.start()

    val connectors: Array[Connector] = server.getConnectors
    val connector = connectors(0).asInstanceOf[ServerConnector]
    val port: Int = connector.getLocalPort

    val client = new DefaultAsyncHttpClient(
      new DefaultAsyncHttpClientConfig.Builder().setKeepEncodingHeader(true).build()
    ) {
      override def close(): Unit = {
        server.stop()
        super.close()
      }
    }

    (client, s"http://localhost:$port")
  }
}
