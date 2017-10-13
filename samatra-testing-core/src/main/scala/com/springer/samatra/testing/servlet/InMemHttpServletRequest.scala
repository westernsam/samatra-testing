package com.springer.samatra.testing.servlet

import java.io.{BufferedReader, ByteArrayInputStream, InputStreamReader}
import java.net.URLDecoder.decode
import java.net.{HttpCookie, URI}
import java.security.Principal
import java.util
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong, AtomicReference}
import java.util.concurrent.{ConcurrentHashMap, CountDownLatch}
import java.util.{Collections, Locale}
import javax.servlet._
import javax.servlet.http._
import scala.collection.JavaConverters._
import com.springer.samatra.testing.servlet.ServletApiHelpers.dateFormat

/**
  * Created by sam on 13/10/17.
  */
class InMemHttpServletRequest(url: String, method: String, headers: Map[String, Seq[String]],
                              body: Option[Array[Byte]], cookies: Seq[Cookie], countDown: CountDownLatch = new CountDownLatch(0),
                              asyncListeners: util.List[AsyncListener] = Collections.emptyList(), contextPath: String = "") extends HttpServletRequest {

  val headersLowerCase: Map[String, Seq[String]] = headers.map { case (k, v) => k.toLowerCase -> v }
  val committed: AtomicBoolean = new AtomicBoolean(false)
  val asyncStarted: AtomicBoolean = new AtomicBoolean(false)
  val asyncContext: AtomicReference[AsyncContext] = new AtomicReference[AsyncContext]()

  val bytes: ByteArrayInputStream = body match {
    case Some(b) => new ByteArrayInputStream(b)
    case None => new ByteArrayInputStream(new Array[Byte](0))
  }
  val uri: URI = new URI(url)
  val path: String = uri.getRawPath
  val attributes: ConcurrentHashMap[String, AnyRef] = new ConcurrentHashMap[String, AnyRef]()

  override def getPathInfo: String = path.substring(getServletPath.length)
  override def getUserPrincipal: Principal = null
  override def getServletPath: String = contextPath
  override def getDateHeader(name: String): Long = if (headersLowerCase.contains(name.toLowerCase)) dateFormat.parse(getHeader(name.toLowerCase)).getTime else -1
  override def getIntHeader(name: String): Int = if (headersLowerCase.contains(name.toLowerCase)) getHeader(name.toLowerCase).toInt else -1
  override def getMethod: String = method
  override def getContextPath: String = ""
  override def isRequestedSessionIdFromUrl: Boolean = false
  override def getPathTranslated: String = path
  override def getRequestedSessionId: String = ""
  override def isRequestedSessionIdFromURL: Boolean = false
  override def logout(): Unit = ()
  override def changeSessionId(): String = ""
  override def getRequestURL: StringBuffer = new StringBuffer(path)
  override def upgrade[T <: HttpUpgradeHandler](handlerClass: Class[T]): T = throw new UnsupportedOperationException
  override def getRequestURI: String = path
  override def isRequestedSessionIdValid: Boolean = true
  override def getAuthType: String = null
  override def authenticate(response: HttpServletResponse): Boolean = true
  override def login(username: String, password: String): Unit = ()
  override def getHeader(name: String): String = headersLowerCase.get(name.toLowerCase).map(_.head).orNull
  override def getHeaders(name: String): util.Enumeration[String] = Collections.enumeration(headersLowerCase.getOrElse(name.toLowerCase, List.empty).asJava)
  override def getQueryString: String = uri.getRawQuery
  override def isUserInRole(role: String): Boolean = true
  override def getRemoteUser: String = ""
  override def getHeaderNames: util.Enumeration[String] = Collections.enumeration(headers.asJava.keySet())
  override def isRequestedSessionIdFromCookie: Boolean = false
  override def getSession(create: Boolean): HttpSession = null
  override def getSession: HttpSession = null
  override def getRemoteAddr: String = "SamatraTestHelper"
  override def getServerName: String = "SamatraTestHelper"
  override def getRemotePort: Int = -1
  override def getRequestDispatcher(path: String): RequestDispatcher = throw new UnsupportedOperationException
  override def isAsyncSupported: Boolean = true
  override def getInputStream: ServletInputStream = {
    if (committed.getAndSet(true)) throw new IllegalStateException("Request body already read")
    new ServletInputStream {
      override def isReady: Boolean = true
      override def isFinished: Boolean = bytes.available() > -1
      override def setReadListener(readListener: ReadListener): Unit = ()
      override def read(): Int = bytes.read()
    }
  }
  override def getReader: BufferedReader = {
    if (committed.getAndSet(true)) throw new IllegalStateException("Request body already read")
    new BufferedReader(new InputStreamReader(bytes))
  }
  override def startAsync(): AsyncContext = throw new UnsupportedOperationException
  override def getAsyncContext: AsyncContext = asyncContext.get
  override def isAsyncStarted: Boolean = asyncStarted.get()
  override def startAsync(originalRequest: ServletRequest, originalResponse: ServletResponse): AsyncContext = {

    asyncStarted.set(true)

    val tout = new AtomicLong(-1)

    val context = new AsyncContext {
      override def hasOriginalRequestAndResponse: Boolean = true
      override def dispatch(): Unit = ()
      override def dispatch(path: String): Unit = ()
      override def dispatch(context: ServletContext, path: String): Unit = ()
      override def getRequest: ServletRequest = originalRequest
      override def getResponse: ServletResponse = originalResponse
      override def start(run: Runnable): Unit = throw new UnsupportedOperationException
      override def setTimeout(timeout: Long): Unit = tout.set(timeout)
      override def getTimeout: Long = tout.get()
      override def complete(): Unit = {
        asyncListeners.asScala.foreach(_.onComplete(new AsyncEvent(asyncContext.get)))
        countDown.countDown()
      }
      override def addListener(listener: AsyncListener): Unit = asyncListeners.add(listener)
      override def createListener[T <: AsyncListener](clazz: Class[T]): T = throw new UnsupportedOperationException
      override def addListener(listener: AsyncListener, servletRequest: ServletRequest, servletResponse: ServletResponse): Unit = asyncListeners.add(listener)
    }
    asyncContext.set(context)
    context
  }

  override def setCharacterEncoding(env: String): Unit = ()
  override def getCharacterEncoding: String = headersLowerCase.get("content-type").map(_.last).map(_.split("=").toList.last).orNull //Content-Type:text/html; charset=utf-8

  override def getServerPort: Int = -1

  override def setAttribute(name: String, o: AnyRef): Unit = attributes.put(name, o)
  override def getAttribute(name: String): AnyRef = attributes.get(name)
  override def getAttributeNames: util.Enumeration[String] = Collections.enumeration(attributes.keySet())
  override def removeAttribute(name: String): Unit = attributes.remove(name)

  override def getCookies: Array[Cookie] = {
    val headerCookies = for {
      c <- headersLowerCase.getOrElse("cookie", List.empty)
      c2 <- HttpCookie.parse(c).asScala
    } yield new Cookie(c2.getName, c2.getValue)

    (headerCookies ++ cookies).toArray
  }

  override def getParameterValues(name: String): Array[String] = getParameterMap.values().asScala.map(_.head).toArray
  override def getParameterNames: util.Enumeration[String] = Collections.enumeration(getParameterMap.keySet())
  override def getParameter(name: String): String = Option(getParameterMap.get(name)).map(_.head).orNull

  override def getParameterMap: util.Map[String, Array[String]] = {
    def parse(qs: String): Map[String, Array[String]] = {
      val tuples: Array[(String, String)] = for {
        p <- qs.split("&")
        keyAndValue = p.split("=", 2)
      } yield decode(keyAndValue(0), "UTF-8") -> decode(keyAndValue(1), "UTF-8")

      tuples.groupBy {
        case (k, _) => k
      }.mapValues(_.map(_._2))
    }

    val bodyParams = if (headersLowerCase.get("content-type").exists(_.head == "application/x-www-form-urlencoded") && body.isDefined) {
      parse(decode(new String(body.get), "UTF-8"))
    } else Map.empty

    (Option(uri.getRawQuery).map(parse).getOrElse(Map.empty) ++ bodyParams).asJava

    //todo - multi-part form
  }
  override def getParts: util.Collection[Part] = throw new UnsupportedOperationException
  override def getPart(name: String): Part = throw new UnsupportedOperationException

  override def getContentLength: Int = body.map(_.length).getOrElse(0)
  override def getContentLengthLong: Long = bytes.available()
  override def getContentType: String = headersLowerCase.get("content-type").map(_.head).orNull

  override def getLocalPort: Int = -1
  override def getServletContext: ServletContext = null
  override def getRemoteHost: String = ""
  override def getLocalAddr: String = ""
  override def getRealPath(path: String): String = path

  override def getScheme: String = "http"

  override def isSecure: Boolean = false
  override def getProtocol: String = "http"
  override def getLocalName: String = ""
  override def getDispatcherType: DispatcherType = DispatcherType.REQUEST
  override def getLocale: Locale = Locale.getDefault
  override def getLocales: util.Enumeration[Locale] = Collections.enumeration(util.Arrays.asList(getLocale))
}
