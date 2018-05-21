
val jettyVersion = "9.4.10.v20180503"

libraryDependencies ++=
  Seq(
    "org.eclipse.jetty" % "jetty-server" % jettyVersion,
    "org.eclipse.jetty" % "jetty-servlet" % jettyVersion,
    "org.eclipse.jetty" % "jetty-servlets" % jettyVersion,

      "com.github.springernature.samatra" %% "samatra" % "v1.5.0" % "test",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test"
  )
