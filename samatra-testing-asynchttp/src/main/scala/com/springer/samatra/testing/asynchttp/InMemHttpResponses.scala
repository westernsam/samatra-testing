package com.springer.samatra.testing.asynchttp

import java.net.SocketAddress
import java.nio.ByteBuffer

import io.netty.channel.local.LocalAddress
import org.asynchttpclient.{HttpResponseBodyPart, HttpResponseStatus}
import org.asynchttpclient.uri.Uri

object InMemHttpResponses {

  private class Status(uri: Uri, sc: Int, st: String) extends HttpResponseStatus(uri, null) {
    override def getRemoteAddress: SocketAddress = new LocalAddress("samatra-inmem")
    override def getLocalAddress: SocketAddress = new LocalAddress("samatra-inmem")
    override def getProtocolText: String = "http"
    override def getProtocolName: String = "http"
    override def getProtocolMajorVersion: Int = 1
    override def getStatusCode: Int = sc
    override def getProtocolMinorVersion: Int = 1
    override def getStatusText: String = st
  }

  private class BodyPart(bytes: Array[Byte]) extends HttpResponseBodyPart(false) {
    override def getBodyPartBytes: Array[Byte] = bytes
    override def length(): Int = bytes.length
    override def getBodyByteBuffer: ByteBuffer = ByteBuffer.wrap(getBodyPartBytes)
  }

  def sendStatus[T](sc: Int, st: String, uri: Uri) : HttpResponseStatus = new Status(uri, sc, st)
  def sendBody[T](bytes: Array[Byte]) : HttpResponseBodyPart = new BodyPart(bytes)
}
