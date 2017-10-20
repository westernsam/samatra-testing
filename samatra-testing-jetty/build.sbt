
val jettyVersion = "9.4.7.v20170914"

libraryDependencies ++=
  Seq(
    "org.eclipse.jetty" % "jetty-server" % jettyVersion,
    "org.eclipse.jetty" % "jetty-servlet" % jettyVersion,
    "org.eclipse.jetty" % "jetty-servlets" % jettyVersion,

    "com.github.springernature" %% "samatra" % "v1.3" % "test",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test"
  )
