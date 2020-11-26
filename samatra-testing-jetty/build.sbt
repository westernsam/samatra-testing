
val jettyVersion = "9.4.35.v20201120"


libraryDependencies ++=
  Seq(
    "org.eclipse.jetty" % "jetty-server" % jettyVersion,
    "org.eclipse.jetty" % "jetty-servlet" % jettyVersion,
    "org.eclipse.jetty" % "jetty-servlets" % jettyVersion,

    "com.github.westernsam.samatra" %% "samatra" % "v1.1" % "test",
    "org.scalatest" %% "scalatest" % "3.2.3" % "test"
  )
