package com.springer.samatra.testing.wiremock

import javax.servlet.ServletContext

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockApp
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.http.{AdminRequestHandler, RequestHandler, StubRequestHandler}
import com.github.tomakehurst.wiremock.servlet.WireMockHandlerDispatchingServlet
import com.springer.samatra.testing.asynchttp.{Backend, ServerConfig}
import com.springer.samatra.testing.servlet.InMemServletContext
import org.asynchttpclient.AsyncHttpClient

class WiremockHelper(otherConfig: ServerConfig = new ServerConfig, wmContextPath: String = "/*") {
  self: Backend =>

  private val admin = new AsyncHttpAdmin(http)
  private val app = new WireMockApp(wireMockConfig, null)

  private val context: ServletContext = {
    val context = new InMemServletContext(new WireMockHandlerDispatchingServlet(), "/")
    context.setAttribute(classOf[AdminRequestHandler].getName, app.buildAdminRequestHandler())
    context.setAttribute(classOf[StubRequestHandler].getName, app.buildStubRequestHandler())
    context
  }

  val wireMock = new WireMock(admin)

  lazy val http: AsyncHttpClient = client(new ServerConfig {
    mount("/__admin/*", new WireMockHandlerDispatchingServlet(), context, Map(RequestHandler.HANDLER_CLASS_KEY -> classOf[AdminRequestHandler].getName), None)
    mount(wmContextPath, new WireMockHandlerDispatchingServlet(), context, Map(RequestHandler.HANDLER_CLASS_KEY -> classOf[StubRequestHandler].getName), None)
  } ++ otherConfig)

}
