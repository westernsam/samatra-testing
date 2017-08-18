name:= "Samatra-testing"

lazy val commonSettings = Seq(
  organization := "com.springernature",
  scalaVersion := "2.12.3",
  scalacOptions ++= Seq("-unchecked", "-deprecation:false", "-feature", "-Xfatal-warnings", "-Xlint"),
  testOptions ++= Tests.Argument("-oDF") :: Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/reports/") :: Nil,
  ivyScala := ivyScala.value map {
    _.copy(overrideScalaVersion = true)
  },
  trapExit := false,
  cancelable in Global := true,
  publish := {},
  crossScalaVersions := Seq(scalaVersion.value, "2.11.7"),

  resolvers ++= Seq(
    "Local Ivy Repository" at s"file:///${Path.userHome.absolutePath}/.ivy2/cache",
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
    "jitpack" at "https://jitpack.io",
    "Repo Tools Sonatype Nexus OSS Releases" at "http://repo.tools.springer-sbm.com:8081/nexus/content/repositories/releases/",
    "MarkLogic" at "http://developer.marklogic.com/maven2"
  )
)

lazy val core = project.in(file("samatra-testing-core"))
  .settings(commonSettings: _*)

lazy val unit = project.in(file("samatra-testing-unit"))
  .settings(commonSettings: _*)
  .dependsOn(core)

lazy val asynchttp = project.in(file("samatra-testing-asynchttp"))
  .settings(commonSettings: _*)
  .dependsOn(core)

lazy val asynchttpjetty = project.in(file("samatra-testing-jetty"))
  .settings(commonSettings: _*)
  .dependsOn(asynchttp)

lazy val wiremock = project.in(file("samatra-testing-wiremock"))
  .settings(commonSettings: _*)
  .dependsOn(asynchttp, asynchttpjetty % "compile->test")

lazy val htmlunitdriver = project.in(file("samatra-testing-htmlunitdriver"))
  .settings(commonSettings: _*)
  .dependsOn(asynchttp)

val samatratesting: sbt.Project = project.in(file("."))
  .aggregate(core, unit, asynchttp, asynchttpjetty, wiremock, htmlunitdriver)
