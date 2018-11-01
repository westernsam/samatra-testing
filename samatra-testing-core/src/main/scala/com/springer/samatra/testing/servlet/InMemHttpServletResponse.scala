package com.springer.samatra.testing.servlet

import java.io.{OutputStream, OutputStreamWriter, PrintWriter}
import java.net.URLEncoder
import java.util
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import java.util.concurrent.{ConcurrentHashMap, CopyOnWriteArrayList}
import java.util.function.Function
import java.util.{Calendar, Locale}
import javax.servlet.http.{Cookie, HttpServletResponse}
import javax.servlet.{ServletOutputStream, WriteListener}

import com.springer.samatra.testing.servlet.ServletApiHelpers._

import scala.collection.JavaConverters._

/**
  * Created by sam on 13/10/17.
  */
class InMemHttpServletResponse(onStatus: OnStatus, onHeader: OnHeader, onCookie: OnCookie, onBody: OnBody) extends HttpServletResponse {

  sealed trait WriterType

  object WriterType {
    case object STREAM extends WriterType
    case object WRITER extends WriterType
    case object UNDECIDED extends WriterType
  }


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

  override def getHeader(name: String): String = respHeaders.asScala.get(name).map(_.asScala.head).orNull
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
