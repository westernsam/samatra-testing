
val jettyVersion = "9.1.5.v20140505"

libraryDependencies ++=
  Seq(
    "org.eclipse.jetty" % "jetty-server" % jettyVersion,
    "org.eclipse.jetty" % "jetty-servlet" % jettyVersion,
    "org.eclipse.jetty" % "jetty-servlets" % jettyVersion,

    "com.github.springernature" %% "samatra" % "v1.3" % "test",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test"
  )
