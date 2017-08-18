package com.springer.samatra.testing.servlet

import java.io._
import java.net.URLDecoder.decode
import java.net.{URI, URLEncoder}
import java.security.Principal
import java.text.SimpleDateFormat
import java.util
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong, AtomicReference}
import java.util.concurrent.{ConcurrentHashMap, CopyOnWriteArrayList, CountDownLatch}
import java.util.function.Function
import java.util.{Calendar, Collections, Locale, TimeZone}
import javax.servlet._
import javax.servlet.http._

import scala.collection.JavaConverters._

object ServletApiHelpers {


  sealed trait WriterType

  object WriterType {
    case object STREAM extends WriterType
    case object WRITER extends WriterType
    case object UNDECIDED extends WriterType
  }

  private val zone = TimeZone.getTimeZone("GMT")
  private val dateFormat: SimpleDateFormat = new SimpleDateFormat("EEE, dd MM yyyy hh:mm:ss z")
  dateFormat.setTimeZone(zone)

  type OnStatus = (Int, String) => Unit
  type OnHeader = (String, Seq[String]) => Unit
  type OnCookie = Cookie => Unit
  type OnBody = Array[Byte] => Unit

  def httpResponse(onStatus: OnStatus, onHeader: OnHeader, onCookie: OnCookie, onBody: OnBody) = new HttpServletResponse {

    val status: AtomicInteger = new AtomicInteger(200)
    val out = new OutputStream() {

      override def write(b: Int): Unit = {
        committed.set(true)
        val bytes = new Array[Byte](1)
        bytes.update(0, b.asInstanceOf[Byte])
        onBody(bytes)
      }

      override def write(b: Array[Byte], off: Int, len: Int): Unit = {
        committed.set(true)
        if (off == 0 && len == b.length) onBody(b) else {
          val cp = new Array[Byte](len)
          System.arraycopy(b, off, cp, 0, len)
          onBody(cp)
        }
      }
    }

    val statusText = new AtomicReference[String]()
    val respHeaders = new ConcurrentHashMap[String, CopyOnWriteArrayList[String]]()
    val respCookies = new CopyOnWriteArrayList[Cookie]()
    val characterEncoding: AtomicReference[String] = new AtomicReference[String]("UTF-8")
    val contentType: AtomicReference[String] = new AtomicReference[String]()
    val committed = new AtomicBoolean(false)

    val writer: PrintWriter = new PrintWriter(new OutputStreamWriter(out))

    val stream: ServletOutputStream = new ServletOutputStream {
      override def isReady: Boolean = true
      override def setWriteListener(writeListener: WriteListener): Unit = throw new UnsupportedOperationException
      override def write(b: Int): Unit = out.write(b)
      override def write(b: Array[Byte], off: Int, len: Int): Unit = out.write(b, off, len)
    }

    private val writerType: AtomicReference[WriterType] = new AtomicReference[WriterType](WriterType.UNDECIDED)

    private def formatDate(date: Long) = {
      val cal = Calendar.getInstance(zone)
      cal.setTimeInMillis(date)
      dateFormat.format(cal.getTime)
    }

    override def sendError(sc: Int, msg: String): Unit = {
      setStatus(sc, msg)
    }

    override def sendError(sc: Int): Unit = setStatus(sc)
    override def getStatus: Int = status.get()

    override def addCookie(cookie: Cookie): Unit = {
      respCookies.add(cookie)
      onCookie(cookie)
    }

    override def getHeader(name: String): String = respHeaders.get(name).asScala.head
    override def setHeader(name: String, value: String): Unit = if (value != null) {
      val hv = new CopyOnWriteArrayList[String]()
      hv.add(value)

      respHeaders.put(name, hv)
      onHeader(name, hv.asScala)
    }

    override def addHeader(name: String, value: String): Unit = {
      val headers = respHeaders.computeIfAbsent(name, new Function[Any, CopyOnWriteArrayList[String]] {
        override def apply(t: Any): CopyOnWriteArrayList[String] = new CopyOnWriteArrayList[String]()
      })
      headers.add(value)
      onHeader(name, headers.asScala)
    }

    override def setIntHeader(name: String, value: Int): Unit = setHeader(name, value.toString)
    override def addDateHeader(name: String, date: Long): Unit = addHeader(name, formatDate(date))

    override def setDateHeader(name: String, date: Long): Unit = setHeader(name, formatDate(date))
    override def encodeURL(url: String): String = URLEncoder.encode(url, "UTF-8")

    override def encodeUrl(url: String): String = URLEncoder.encode(url, "UTF-8")
    override def getHeaders(name: String): util.Collection[String] = respHeaders.get(name)

    override def encodeRedirectUrl(url: String): String = encodeUrl(url)
    override def encodeRedirectURL(url: String): String = encodeURL(url)
    override def sendRedirect(location: String): Unit = {
      setStatus(302)
      addHeader("Location", location)
    }
    override def setStatus(sc: Int): Unit = setStatus(sc, sc match {
      case 100 => "Continue"
      case 101 => "Switching Protocols"
      case 102 => "Processing"
      case 200 => "OK"
      case 201 => "Created"
      case 202 => "Accepted"
      case 203 => "Non Authoritative Information"
      case 204 => "No Content"
      case 205 => "Reset Content"
      case 206 => "Partial Content"
      case 207 => "Multi-Status"
      case 300 => "Multiple Choices"
      case 301 => "Moved Permanently"
      case 302 => "Moved Temporarily"
      case 303 => "See Other"
      case 304 => "Not Modified"
      case 305 => "Use Proxy"
      case 307 => "Temporary Redirect"
      case 400 => "Bad Request"
      case 401 => "Unauthorized"
      case 402 => "Payment Required"
      case 403 => "Forbidden"
      case 404 => "Not Found"
      case 405 => "Method Not Allowed"
      case 406 => "Not Acceptable"
      case 407 => "Proxy Authentication Required"
      case 408 => "Request Timeout"
      case 409 => "Conflict"
      case 410 => "Gone"
      case 411 => "Length Required"
      case 412 => "Precondition Failed"
      case 413 => "Request Entity Too Large"
      case 414 => "Request-URI Too Long"
      case 415 => "Unsupported Media Type"
      case 416 => "Requested Range Not Satisfiable"
      case 417 => "Expectation Failed"
      case 422 => "Unprocessable Entity"
      case 423 => "Locked"
      case 424 => "Failed Dependency"
      case 500 => "Server Error"
      case 501 => "Not Implemented"
      case 502 => "Bad Gateway"
      case 503 => "Service Unavailable"
      case 504 => "Gateway Timeout"
      case 505 => "HTTP Version Not Supported"
      case 507 => "Insufficient Storage"
      case _ => ""
    })
    override def setStatus(sc: Int, sm: String): Unit = {
      status.set(sc)
      statusText.set(sm)
      onStatus(sc, sm)
    }
    override def getHeaderNames: util.Collection[String] = respHeaders.keySet()
    override def containsHeader(name: String): Boolean = respHeaders.containsKey(name)
    override def addIntHeader(name: String, value: Int): Unit = addHeader(name, value.toString)
    override def getBufferSize: Int = -1
    override def resetBuffer(): Unit = ()
    override def setContentType(`type`: String): Unit = {
      contentType.set(`type`)
      setHeader("Content-Type", `type`)
    }
    override def setBufferSize(size: Int): Unit = ()
    override def isCommitted: Boolean = committed.get()
    override def setCharacterEncoding(charset: String): Unit = characterEncoding.set(charset)
    override def setContentLength(len: Int): Unit = setHeader("Content-Length", len.toString)
    override def setContentLengthLong(len: Long): Unit = setHeader("Content-Length", len.toString)

    override def getCharacterEncoding: String = characterEncoding.get
    override def flushBuffer(): Unit = {
      writer.flush()
      stream.flush()
    }
    override def getContentType: String = Option(contentType.get()).getOrElse("")
    override def reset(): Unit = ()

    override def getWriter: PrintWriter = {
      if (writerType.getAndSet(WriterType.WRITER) == WriterType.STREAM) throw new IllegalStateException("Using stream")
      writer
    }

    override def getOutputStream: ServletOutputStream = {
      if (writerType.getAndSet(WriterType.STREAM) == WriterType.WRITER) throw new IllegalStateException("Using stream")
      stream
    }

    override def getLocale: Locale = Locale.getDefault
    override def setLocale(loc: Locale): Unit = ()
  }

  def httpServletRequest(url: String, method: String, headers: Map[String, Seq[String]], body: Option[Array[Byte]], cookies: Seq[Cookie], countDown: CountDownLatch = new CountDownLatch(0), asyncListeners: util.List[AsyncListener] = Collections.emptyList(), contextPath: String = ""): HttpServletRequest = {

    val headersLowerCase = headers.map { case (k, v) => k.toLowerCase -> v }
    val committed = new AtomicBoolean(false)
    val asyncStarted = new AtomicBoolean(false)
    val asyncContext = new AtomicReference[AsyncContext]()

    val bytes = body match {
      case Some(b) => new ByteArrayInputStream(b)
      case None => new ByteArrayInputStream(new Array[Byte](0))
    }
    val uri = new URI(url)
    val path = uri.getRawPath
    val attributes = new ConcurrentHashMap[String, AnyRef]()

    new HttpServletRequest {
      override def getPathInfo: String = path
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
      override def getHeaders(name: String): util.Enumeration[String] = Collections.enumeration(headersLowerCase(name.toLowerCase).asJava)
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

      override def getCookies: Array[Cookie] = cookies.toArray

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
  }
}

