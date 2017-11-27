val jettyVersion = "9.4.7.v20170914"

libraryDependencies ++=
  Seq(
    "javax.servlet" % "javax.servlet-api" % "3.1.0",
    "org.slf4j" % "slf4j-api" % "1.7.23",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test"
  )
