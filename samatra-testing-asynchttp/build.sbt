val jettyVersion = "9.1.5.v20140505"

libraryDependencies ++=
  Seq(
    "org.asynchttpclient" % "async-http-client" % "2.0.32",

    "com.github.springernature" %% "samatra" % "v1.3" % "test",
    "org.eclipse.jetty" % "jetty-server" % jettyVersion % "test",
    "org.eclipse.jetty" % "jetty-servlets" % jettyVersion % "test",

    "org.scalatest" %% "scalatest" % "3.0.0" % "test"
  )