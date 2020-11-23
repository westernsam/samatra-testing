
val jettyVersion = "9.4.12.v20180830"

libraryDependencies ++=
  Seq(
    "org.eclipse.jetty" % "jetty-server" % jettyVersion,
    "org.eclipse.jetty" % "jetty-servlet" % jettyVersion,
    "org.eclipse.jetty" % "jetty-servlets" % jettyVersion,

    "com.github.westernsam.samatra" %% "samatra" % "v1.0" % "test",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test"
  )
