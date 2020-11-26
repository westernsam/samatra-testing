val jettyVersion = "9.4.35.v20201120"

libraryDependencies ++=
  Seq(
    "org.asynchttpclient" % "async-http-client" % "2.12.1",
    "javax.websocket" % "javax.websocket-api" % "1.1",

    "com.github.westernsam.samatra" %% "samatra" % "v1.1" % "test",
    "com.github.westernsam.samatra" %% "samatra-websockets" % "v1.1",
    "org.scalatest" %% "scalatest" % "3.2.3",
    "org.eclipse.jetty" % "jetty-server" % jettyVersion % "test",
    "org.eclipse.jetty" % "jetty-servlet" % jettyVersion % "test",
    "org.eclipse.jetty" % "jetty-servlets" % jettyVersion % "test"

  )