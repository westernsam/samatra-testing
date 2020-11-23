val jettyVersion = "9.4.12.v20180830"

libraryDependencies ++=
  Seq(
    "org.asynchttpclient" % "async-http-client" % "2.5.2",
    "javax.websocket" % "javax.websocket-api" % "1.1",

    "com.github.westernsam.samatra" %% "samatra" % "v1.0" % "test",
    "com.github.westernsam.samatra" %% "samatra-websockets" % "v1.0",
    "org.scalatest" %% "scalatest" % "3.0.5",
    "org.eclipse.jetty" % "jetty-server" % jettyVersion % "test",
    "org.eclipse.jetty" % "jetty-servlet" % jettyVersion % "test",
    "org.eclipse.jetty" % "jetty-servlets" % jettyVersion % "test"

  )