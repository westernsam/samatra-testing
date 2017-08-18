package com.springer.samatra.testing.asynchttp

import java.util
import javax.servlet.{Filter, Servlet, ServletContext}

import com.springer.samatra.testing.servlet.InMemServletContext
import org.asynchttpclient.AsyncHttpClient

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

trait Backend {

  implicit def toJavaMap(headers: Map[String, Seq[String]]): util.Map[String, util.Collection[String]] = headers.mapValues(_.asJava.asInstanceOf[util.Collection[String]]).asJava

  def client(serverConfig: ServerConfig): AsyncHttpClient
  def clientAndBaseUrl(serverConfig: ServerConfig): (AsyncHttpClient, String)
}

class ServerConfig { self =>
  lazy val filters: ArrayBuffer[(String, Filter)] = new ArrayBuffer[(String, Filter)]()
  lazy val routes: ArrayBuffer[(String, Servlet, ServletContext, Map[String, String])] = new ArrayBuffer[(String, Servlet, ServletContext, Map[String, String])]()

  def mount(path: String, f: Filter): Unit = filters.append(path -> f)
  def mount(path: String, s: Servlet): Unit = mount(path, s, new InMemServletContext(s, path))
  def mount(path: String, s: Servlet, context: ServletContext, initParams: Map[String, String] = Map.empty): Unit = routes.append((path, s, context, initParams))

  def ++(other: ServerConfig) : ServerConfig = new ServerConfig() {
    override lazy val filters: ArrayBuffer[(String, Filter)] = self.filters ++ other.filters
    override lazy val routes: ArrayBuffer[(String, Servlet, ServletContext, Map[String, String])] = self.routes ++ other.routes
  }
}
