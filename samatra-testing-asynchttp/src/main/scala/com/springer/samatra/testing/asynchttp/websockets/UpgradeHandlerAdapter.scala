package com.springer.samatra.testing.asynchttp.websockets

import org.asynchttpclient.AsyncHandler
import org.asynchttpclient.ws.{UpgradeHandler, WebSocket, WebSocketUpgradeHandler}

class UpgradeHandlerAdapter(handler: AsyncHandler[_]) extends UpgradeHandler[WebSocket] {
  override def onFailure(t: Throwable): Unit = handler match {
    case ws: WebSocketUpgradeHandler => ws.onFailure(t)
    case _ =>
  }
  override def onSuccess(t: WebSocket): Unit = handler match {
    case ws: WebSocketUpgradeHandler => ws.onSuccess(t)
    case _ =>
  }
}