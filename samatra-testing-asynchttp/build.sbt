val jettyVersion = "9.4.7.v20170914"

libraryDependencies ++=
  Seq(
    "org.asynchttpclient" % "async-http-client" % "2.1.0",
    "javax.websocket" % "javax.websocket-api" % "1.1",

    "com.github.springernature.samatra" %% "samatra" % "v1.5.0" % "test",
    "com.github.springernature.samatra" %% "samatra-websockets" % "v1.5.0" % "test",
    "org.eclipse.jetty" % "jetty-server" % jettyVersion % "test",
    "org.eclipse.jetty" % "jetty-servlet" % jettyVersion % "test",
    "org.eclipse.jetty" % "jetty-servlets" % jettyVersion % "test",

    "org.scalatest" %% "scalatest" % "3.0.0" % "test"
  )