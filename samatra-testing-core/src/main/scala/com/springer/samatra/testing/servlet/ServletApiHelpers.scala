package com.springer.samatra.testing.servlet

import java.security.Principal
import java.text.SimpleDateFormat
import java.util
import java.util.{Collections, TimeZone}
import java.util.concurrent.CountDownLatch

import javax.servlet._
import javax.servlet.http._

object ServletApiHelpers {

  val zone: TimeZone = TimeZone.getTimeZone("GMT")
  val dateFormat: SimpleDateFormat = new SimpleDateFormat("EEE, dd MM yyyy hh:mm:ss z")
  dateFormat.setTimeZone(zone)

  type OnStatus = (Int, String) => Unit
  type OnHeader = (String, Seq[String]) => Unit
  type OnCookie = Cookie => Unit
  type OnBody = Array[Byte] => Unit

  def httpResponse(onStatus: OnStatus, onHeader: OnHeader, onCookie: OnCookie, onBody: OnBody) = new InMemHttpServletResponse(onStatus, onHeader, onCookie, onBody)

  def httpServletRequest(protocol: String, url: String, method: String, headers: Map[String, Seq[String]],
                         body: Option[Array[Byte]], cookies: Seq[Cookie], countDown: CountDownLatch = new CountDownLatch(0),
                         asyncListeners: util.List[AsyncListener] = Collections.emptyList(), contextPath: String = "", userPrincipal: Option[Principal] = None): HttpServletRequest = {

    new InMemHttpServletRequest(protocol, url, method, headers, body, cookies, countDown, asyncListeners, contextPath, userPrincipal)
  }
}

