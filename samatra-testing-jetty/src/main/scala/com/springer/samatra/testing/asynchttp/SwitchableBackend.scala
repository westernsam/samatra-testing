package com.springer.samatra.testing.asynchttp

import org.asynchttpclient.AsyncHttpClient

trait SwitchableBackend extends Backend {

  override def client(serverConfig: ServerConfig): AsyncHttpClient = backend.client(serverConfig)
  override def clientAndBaseUrl(serverConfig: ServerConfig): (AsyncHttpClient, String) = backend.clientAndBaseUrl(serverConfig)

  lazy val backend: Backend = {
    Option(System.getProperty("samatra-testing-mode")) match {
      case Some("jetty") => new JettyBacked {}
      case Some("inmemory") => new InMemoryBackend {}
      case _ => throw new IllegalArgumentException("Set property samatra-testing-mode to one of jetty or inmemory")
    }
  }
}
