package com.springer.samatra.testing.servlet

import java.io.InputStream
import java.net.URL
import java.util
import java.util.concurrent.ConcurrentHashMap
import java.util.{Collections, EventListener}
import javax.servlet._
import javax.servlet.descriptor.JspConfigDescriptor

import org.slf4j.LoggerFactory

class InMemServletregistration(servlet: Servlet, initParams: ConcurrentHashMap[String, String]) extends ServletRegistration {

  override def addMapping(urlPatterns: String*): util.Set[String] = throw new UnsupportedOperationException
  override def getMappings: util.Collection[String] = Collections.emptyList()
  override def getRunAsRole: String = throw new UnsupportedOperationException
  override def getName: String = servlet.getClass.getSimpleName
  override def setInitParameters(initParameters: util.Map[String, String]): util.Set[String] = {
    initParams.putAll(initParameters)
    initParams.keySet()
  }
  override def getInitParameters: util.Map[String, String] = initParams
  override def getInitParameter(name: String): String = initParams.get(name)
  override def setInitParameter(name: String, value: String): Boolean = {
    val bool = initParams.contains(name)
    initParams.put(name, value)
    !bool
  }
  override def getClassName: String = servlet.getClass.getName
}

class InMemServletContext(servlet: Servlet, contextPath: String) extends ServletContext {
  val logger  = LoggerFactory.getLogger(getClass)

  val attributes = new ConcurrentHashMap[String, Object]()
  val initParams = new ConcurrentHashMap[String, String]()

  override def getServletRegistration(servletName: String): ServletRegistration = new InMemServletregistration(servlet, initParams)
  override def getServletNames: util.Enumeration[String] = Collections.emptyEnumeration()
  override def getFilterRegistration(filterName: String): FilterRegistration = throw new UnsupportedOperationException
  override def getResource(path: String): URL = null
  override def createServlet[T <: Servlet](clazz: Class[T]): T = throw new UnsupportedOperationException
  override def getContextPath: String = contextPath
  override def getInitParameterNames: util.Enumeration[String] = Collections.emptyEnumeration()
  override def getVirtualServerName: String = "in-mem"
  override def getMajorVersion: Int = 1
  override def getFilterRegistrations: util.Map[String, _ <: FilterRegistration] = throw new UnsupportedOperationException
  override def getServlet(name: String): Servlet = servlet
  override def setSessionTrackingModes(sessionTrackingModes: util.Set[SessionTrackingMode]): Unit = throw new UnsupportedOperationException
  override def getServletRegistrations: util.Map[String, _ <: ServletRegistration] = {
    val regs = new util.HashMap[String, ServletRegistration]()
    regs.put(servlet.getClass.getSimpleName, getServletRegistration(servlet.getClass.getSimpleName))
    regs
  }
  override def getRequestDispatcher(path: String): RequestDispatcher = throw new UnsupportedOperationException
  override def createFilter[T <: Filter](clazz: Class[T]): T = throw new UnsupportedOperationException
  override def getDefaultSessionTrackingModes: util.Set[SessionTrackingMode] = throw new UnsupportedOperationException
  override def getResourcePaths(path: String): util.Set[String] = throw new UnsupportedOperationException
  override def getEffectiveMajorVersion: Int = 1
  override def getInitParameter(name: String): String = initParams.get(name)
  override def addFilter(filterName: String, className: String): FilterRegistration.Dynamic = throw new UnsupportedOperationException
  override def addFilter(filterName: String, filter: Filter): FilterRegistration.Dynamic = throw new UnsupportedOperationException
  override def addFilter(filterName: String, filterClass: Class[_ <: Filter]): FilterRegistration.Dynamic = throw new UnsupportedOperationException
  override def declareRoles(roleNames: String*): Unit = throw new UnsupportedOperationException
  override def getServletContextName: String = throw new UnsupportedOperationException
  override def getSessionCookieConfig: SessionCookieConfig = throw new UnsupportedOperationException
  override def log(msg: String): Unit = logger.info(msg)
  override def log(exception: Exception, msg: String): Unit = logger.warn(msg, exception)
  override def log(message: String, throwable: Throwable): Unit = logger.warn(message, throwable)
  override def getMinorVersion: Int = 1
  override def getEffectiveSessionTrackingModes: util.Set[SessionTrackingMode] = throw new UnsupportedOperationException
  override def getRealPath(path: String): String = s"$contextPath/$path"
  override def getClassLoader: ClassLoader = getClass.getClassLoader
  override def getAttributeNames: util.Enumeration[String] = attributes.keys()
  override def setAttribute(name: String, `object`: Object): Unit = if (name != null) attributes.put(name, `object`)
  override def getAttribute(name: String): Object = if (name == null) null else attributes.get(name)
  override def removeAttribute(name: String): Unit = attributes.remove(name)
  override def getServlets: util.Enumeration[Servlet] = throw new UnsupportedOperationException
  override def addListener(className: String): Unit = throw new UnsupportedOperationException
  override def addListener[T <: EventListener](t: T): Unit = throw new UnsupportedOperationException
  override def addListener(listenerClass: Class[_ <: EventListener]): Unit = throw new UnsupportedOperationException
  override def getServerInfo: String = "samatra-inmem"
  override def getEffectiveMinorVersion: Int = 1
  override def getMimeType(file: String): String = ""
  override def addServlet(servletName: String, className: String): ServletRegistration.Dynamic = throw new UnsupportedOperationException
  override def addServlet(servletName: String, servlet: Servlet): ServletRegistration.Dynamic = throw new UnsupportedOperationException
  override def addServlet(servletName: String, servletClass: Class[_ <: Servlet]): ServletRegistration.Dynamic = throw new UnsupportedOperationException
  override def getResourceAsStream(path: String): InputStream = throw new UnsupportedOperationException
  override def createListener[T <: EventListener](clazz: Class[T]): T = throw new UnsupportedOperationException
  override def setInitParameter(name: String, value: String): Boolean = {
    val bool = initParams.contains(name)
    initParams.put(name, value)
    !bool
  }
  override def getJspConfigDescriptor: JspConfigDescriptor = throw new UnsupportedOperationException
  override def getContext(uripath: String): ServletContext = throw new UnsupportedOperationException
  override def getNamedDispatcher(name: String): RequestDispatcher = throw new UnsupportedOperationException
}
