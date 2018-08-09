val jettyVersion = "9.4.11.v20180605"

libraryDependencies ++=
  Seq(
    "javax.servlet" % "javax.servlet-api" % "3.1.0",
    "org.slf4j" % "slf4j-api" % "1.7.25",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test"
  )
