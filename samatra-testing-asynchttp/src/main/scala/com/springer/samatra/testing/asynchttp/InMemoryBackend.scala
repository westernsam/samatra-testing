package com.springer.samatra.testing.asynchttp

import java.io.{ByteArrayOutputStream, InputStream, OutputStream}
import java.util.concurrent._
import java.util.function.Predicate

import com.springer.samatra.testing.asynchttp.InMemHttpResponses.{sendBody, sendStatus}
import com.springer.samatra.testing.asynchttp.websockets.WebSocketUpgradeFilter
import com.springer.samatra.testing.servlet.InMemFilterChain
import com.springer.samatra.testing.servlet.ServletApiHelpers.{httpResponse, httpServletRequest}
import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaders}
import javax.servlet._
import javax.servlet.http.{Cookie, HttpServletRequest, HttpServletResponse}
import org.asynchttpclient._
import org.asynchttpclient.uri.Uri
import org.asynchttpclient.ws.WebSocketUpgradeHandler

import scala.collection.JavaConverters._
import scala.collection.immutable.AbstractMap
import scala.concurrent.{ExecutionContext, Future}


trait InMemoryBackend extends Backend {

  private implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5))

  override def clientAndBaseUrl(serverConfig: ServerConfig): (AsyncHttpClient, String) = (client(serverConfig), "http://samatra-inmem")

  override def client(serverConfig: ServerConfig): AsyncHttpClient = new AsyncHttpClient {
    override def preparePatch(url: String): BoundRequestBuilder = new BoundRequestBuilder(this, "PATCH", false).setUrl(s"http://samatra-inmem$url")
    override def preparePost(url: String): BoundRequestBuilder = new BoundRequestBuilder(this, "POST", false).setUrl(s"http://samatra-inmem$url")
    override def prepareGet(url: String): BoundRequestBuilder = {
      val (scheme, path) = if (url.indexOf(":") > -1) (url.split(":")(0), url.split(":")(1)) else ("http", url)
      new BoundRequestBuilder(this, "GET", false).setUrl(s"$scheme://samatra-inmem$path")
    }
    override def prepare(method: String, url: String): BoundRequestBuilder = new BoundRequestBuilder(this, method, false).setUrl(s"http://samatra-inmem$url")
    override def prepareHead(url: String): BoundRequestBuilder = new BoundRequestBuilder(this, "HEAD", false).setUrl(s"http://samatra-inmem$url")
    override def prepareDelete(url: String): BoundRequestBuilder = new BoundRequestBuilder(this, "DELETE", false).setUrl(s"http://samatra-inmem$url")
    override def preparePut(url: String): BoundRequestBuilder = new BoundRequestBuilder(this, "PUT", false).setUrl(s"http://samatra-inmem$url")
    override def prepareOptions(url: String): BoundRequestBuilder = new BoundRequestBuilder(this, "OPTIONS", false).setUrl(s"http://samatra-inmem$url")
    override def prepareConnect(url: String): BoundRequestBuilder = new BoundRequestBuilder(this, "CONNECT", false).setUrl(s"http://samatra-inmem$url")
    override def prepareTrace(url: String): BoundRequestBuilder = new BoundRequestBuilder(this, "TRACE", false).setUrl(s"http://samatra-inmem$url")
    override def prepareRequest(request: Request): BoundRequestBuilder = throw new UnsupportedOperationException
    override def prepareRequest(requestBuilder: RequestBuilder): BoundRequestBuilder = throw new UnsupportedOperationException
    override def setSignatureCalculator(signatureCalculator: SignatureCalculator): AsyncHttpClient = throw new UnsupportedOperationException
    override def isClosed: Boolean = false
    override def executeRequest(requestBuilder: RequestBuilder): ListenableFuture[Response] = executeRequest(requestBuilder.build())
    override def executeRequest[T](requestBuilder: RequestBuilder, handler: AsyncHandler[T]): ListenableFuture[T] = executeRequest(requestBuilder.build(), handler)

    override def executeRequest[T](asyncRequest: Request, handler: AsyncHandler[T]): ListenableFuture[T] = {
      val path = asyncRequest.getUri.toRelativeUrl

      val contextAndServlet: Option[(String, Servlet, ServletContext, Map[String, String])] = findContextPath(serverConfig, path)
      val servletPath = contextAndServlet.map { case (c, _, _, _) => c.substring(0, c.length - 2) }.getOrElse("")
      val asyncLatch = new CountDownLatch(1)
      val asyncListeners = new CopyOnWriteArrayList[AsyncListener]()

      new InMemListenableFuture[T](handler, Future {
        val asyncHandler = new InMemAsyncHandler[T](handler, asyncRequest.getUri)
        val request: HttpServletRequest = makeRequest(asyncRequest, path, servletPath, asyncLatch, asyncListeners)
        val response: HttpServletResponse = mkResponse(asyncHandler, asyncRequest.getUri)

        val filters =
          handler match {
            case h: WebSocketUpgradeHandler =>
              serverConfig.filters :+ (("/*", new WebSocketUpgradeFilter(websockets = serverConfig.websockets.toSeq, h), None))

            case _ => serverConfig.filters
          }

        try {
          InMemFilterChain(request, response, filters.toSeq, contextAndServlet.map(s => (s._2, s._3, s._4)), asyncLatch, asyncListeners)
        } catch {
          case t: Throwable => handler.onThrowable(t); throw t
        } finally {
          response.flushBuffer()
          asyncHandler.flush()
        }
      })
    }

    override def executeRequest(request: Request): ListenableFuture[Response] = executeRequest(request, new AsyncCompletionHandlerBase())
    override def close(): Unit = ()

    override def flushChannelPoolPartitions(predicate: Predicate[AnyRef]): Unit = ()
    override def getClientStats: ClientStats = throw new UnsupportedOperationException
    override def getConfig: AsyncHttpClientConfig = throw new UnsupportedOperationException
  }

  private def mkResponse[T](handler: AsyncHandler[T], uri: Uri) = httpResponse(
    onStatus = (sc, st) => {
      handler.onStatusReceived(sendStatus(sc, st, uri))
    },

    onHeader = (name, values) => {
      val headers = new DefaultHttpHeaders()
      headers.add(name, values.asJava)
      handler.onHeadersReceived(headers)
    },

    onCookie = c => {
      val cookie: String = s"${c.getName}=${c.getValue}" +
        Option(c.getPath).map(p => s";Path=$p").getOrElse("") +
        Option(c.getDomain).map(d => s";Domain=$d").getOrElse("") +
        Option(c.isHttpOnly).map(_ => s";HttpOnly").getOrElse("")

      val headers = new DefaultHttpHeaders()
      headers.add("Set-Cookie", cookie)
      handler.onHeadersReceived(headers)
    },

    onBody = bytes => {
      handler.onBodyPartReceived(sendBody(bytes))
    }
  )

  private def makeRequest[T](asyncRequest: Request, path: String, servletPath: String, asyncLatch: CountDownLatch, listeners: CopyOnWriteArrayList[AsyncListener]): HttpServletRequest = {
    val headers: HttpHeaders = asyncRequest.getHeaders
    val scalaHeaders: Map[String, Seq[String]] = new AbstractMap[String, Seq[String]] {
      override def iterator: Iterator[(String, Seq[String])] = {
        for {k <- headers.names().asScala} yield k -> headers.getAll(k).asScala.toSeq
      }.toSeq.iterator
      override def get(key: String): Option[Seq[String]] = if (headers.contains(key)) Some(headers.getAll(key).asScala.toSeq) else None

      override def removed(key: String): Map[String, Seq[String]] = throw new UnsupportedOperationException

      override def updated[V1 >: Seq[String]](key: String, value: V1): Map[String, V1] = throw new UnsupportedOperationException
    }

    val maybeBytes =
      if (asyncRequest.getStringData != null)
        Some(asyncRequest.getStringData.getBytes)
      else if (asyncRequest.getByteData != null)
        Some(asyncRequest.getByteData)
      else if (asyncRequest.getByteBufferData != null)
        Some(asyncRequest.getByteBufferData.array())
      else if (asyncRequest.getStreamData != null) {
        def copy(input: InputStream, output: OutputStream): Unit = {
          val buffer = new Array[Byte](4 * 1024)
          var n = 0
          while ( {
            n = input.read(buffer)
            n > -1
          }) {
            output.write(buffer, 0, n)
          }
        }

        val stream = new ByteArrayOutputStream()
        copy(asyncRequest.getStreamData, stream)
        Some(stream.toByteArray)
      }
      else None

    val cookies = asyncRequest.getCookies.asScala.map(c => {
      val cookie = new Cookie(c.name(), c.value())
      Option(c.domain()).foreach(cookie.setDomain)
      cookie.setHttpOnly(c.isHttpOnly)
      cookie.setPath(c.path())

      cookie
    })

    httpServletRequest(asyncRequest.getUri.getScheme, path, asyncRequest.getMethod, scalaHeaders, maybeBytes, cookies.toSeq, asyncLatch, listeners, servletPath)
  }

  private def findContextPath(serverConfig: ServerConfig, path: String): Option[(String, Servlet, ServletContext, Map[String, String])] = {
    def pathMatches(p: String) = path.startsWith(p.substring(0, p.length - 2))

    val sorted = serverConfig.routes.sortBy {
      case (p, _, _, _) => if (pathMatches(p)) p.length - 2 else -1
    }.reverse

    sorted.find {
      case (p, _, _, _) => pathMatches(p)
    }
  }
}