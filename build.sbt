import sbt.Keys.publishTo

name := "samatra-testing"

lazy val commonSettings = Seq(
  organization := "com.springernature",
  scalaVersion := "2.12.6",
  scalacOptions ++= Seq("-unchecked", "-deprecation:false", "-feature", "-Xfatal-warnings", "-Xlint"),
  testOptions ++= Tests.Argument("-oDF") :: Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/reports/") :: Nil,
  trapExit := false,
  cancelable in Global := true,
  publish := {},
//  crossScalaVersions := Seq(scalaVersion.value, "2.11.7"),

  resolvers ++= Seq(
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
    "jitpack" at "https://jitpack.io"
  ),
  publishMavenStyle := true,
  publishTo := {
    Some(Resolver.file("Local Maven Repository", new File(Path.userHome.absolutePath + "~/.m2/repository")))
  }
)

lazy val `samatra-testing-core` = project.in(file("samatra-testing-core"))
  .settings(commonSettings: _*)

lazy val `samatra-testing-unit` = project.in(file("samatra-testing-unit"))
  .settings(commonSettings: _*)
  .dependsOn(`samatra-testing-core`)

lazy val `samatra-testing-asynchttp` = project.in(file("samatra-testing-asynchttp"))
  .settings(commonSettings: _*)
  .dependsOn(`samatra-testing-core`)

lazy val `samatra-testing-jetty` = project.in(file("samatra-testing-jetty"))
  .settings(commonSettings: _*)
  .dependsOn(`samatra-testing-asynchttp`)

lazy val `samatra-testing-wiremock` = project.in(file("samatra-testing-wiremock"))
  .settings(commonSettings: _*)
  .dependsOn(`samatra-testing-asynchttp`, `samatra-testing-jetty` % "compile->test")

lazy val `samatra-testing-htmlunitdriver` = project.in(file("samatra-testing-htmlunitdriver"))
  .settings(commonSettings: _*)
  .dependsOn(`samatra-testing-asynchttp`)

val `samatra-testing`: sbt.Project = project.in(file("."))
  .settings(commonSettings: _*)
  .aggregate(`samatra-testing-core`, `samatra-testing-unit`, `samatra-testing-asynchttp`, `samatra-testing-jetty`, `samatra-testing-wiremock`, `samatra-testing-htmlunitdriver`)
