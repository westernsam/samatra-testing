package com.springer.samatra.testing.unit

import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ConcurrentHashMap, CopyOnWriteArrayList, CountDownLatch, TimeUnit}
import java.util.function.Function

import javax.servlet.http.{Cookie, HttpServletRequest, HttpServletResponse}
import com.springer.samatra.testing.servlet.ServletApiHelpers._
import javax.servlet.{AsyncEvent, AsyncListener}

import scala.collection.JavaConverters._

object InMemoryHttpReqResp {

  type Response = (Int, Map[String, Seq[String]], Seq[Cookie], Array[Byte])

  def apply(req: HttpServletRequest, process: (HttpServletRequest, HttpServletResponse) => Unit, asyncLatch: CountDownLatch, asyncListeners: java.util.List[AsyncListener]): Response = {
    val status: AtomicInteger = new AtomicInteger(200)
    val respHeaders = new ConcurrentHashMap[String, CopyOnWriteArrayList[String]]()
    val respCookies = new CopyOnWriteArrayList[Cookie]()
    val out = new ByteArrayOutputStream()

    val response = httpResponse(
      (sc, _) => status.set(sc),
      (name, values) => {
        respHeaders.computeIfAbsent(name, new Function[Any, CopyOnWriteArrayList[String]] {
          override def apply(t: Any): CopyOnWriteArrayList[String] = new CopyOnWriteArrayList[String]()
        }).addAll(values.asJava)},
      c => respCookies.add(c),
      bytes => out.write(bytes)
    )
    try {
      process(req, response)
      if (req.isAsyncStarted) {
        if (!asyncLatch.await(req.getAsyncContext.getTimeout, TimeUnit.MILLISECONDS)) {
          asyncListeners.asScala.foreach(_.onTimeout(new AsyncEvent(req.getAsyncContext)))
        }
      }
    } catch {
      case t: Throwable =>
        response.sendError(500)
        req.setAttribute("javax.servlet.error.exception", t)
    } finally {
      response.flushBuffer()
    }

    (status.get(), respHeaders.asScala.mapValues(_.asScala.toSeq).toMap, respCookies.asScala, out.toByteArray)
  }
}