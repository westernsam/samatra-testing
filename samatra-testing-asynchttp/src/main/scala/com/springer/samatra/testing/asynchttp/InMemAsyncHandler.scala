package com.springer.samatra.testing.asynchttp

import java.util.concurrent.atomic.AtomicBoolean

import com.springer.samatra.testing.asynchttp.InMemHttpResponses.sendStatus
import io.netty.handler.codec.http.DefaultHttpHeaders
import org.asynchttpclient.uri.Uri
import org.asynchttpclient.{AsyncHandler, HttpResponseBodyPart, HttpResponseHeaders, HttpResponseStatus}

class InMemAsyncHandler[T](handler: AsyncHandler[T], uri: Uri) extends AsyncHandler[T] {

  val headersSent = new AtomicBoolean(false)
  val statusSent = new AtomicBoolean(false)

  val hs = new DefaultHttpHeaders()

  def flush(): Unit = {
    if (!statusSent.getAndSet(true))
      handler.onStatusReceived(sendStatus(200, "Ok", uri))

    if (!headersSent.getAndSet(true))
      handler.onHeadersReceived(new HttpResponseHeaders(hs))
  }

  override def onStatusReceived(responseStatus: HttpResponseStatus): AsyncHandler.State = {
    statusSent.set(true)
    handler.onStatusReceived(responseStatus)
  }

  override def onHeadersReceived(headers: HttpResponseHeaders): AsyncHandler.State = {
    if (!statusSent.getAndSet(true))
      handler.onStatusReceived(sendStatus(200, "Ok", uri))

    hs.add(headers.getHeaders)

    AsyncHandler.State.CONTINUE
  }

  override def onBodyPartReceived(bodyPart: HttpResponseBodyPart): AsyncHandler.State = {
    flush()

    handler.onBodyPartReceived(bodyPart)
  }

  override def onCompleted(): T = handler.onCompleted()
  override def onThrowable(t: Throwable): Unit = handler.onThrowable(t)
}
