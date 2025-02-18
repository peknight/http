ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.6.2"

ThisBuild / organization := "com.peknight"

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-unchecked",
    "-Xfatal-warnings",
    "-language:strictEquality",
    "-Xmax-inlines:64"
  ),
)

lazy val http = (project in file("."))
  .aggregate(
    httpCore.jvm,
    httpCore.js,
  )
  .settings(commonSettings)
  .settings(
    name := "http",
  )

lazy val httpCore = (crossProject(JSPlatform, JVMPlatform) in file("http-core"))
  .settings(commonSettings)
  .settings(
    name := "http-core",
    libraryDependencies ++= Seq(
      "com.peknight" %%% "codec-http4s" % pekCodecVersion,
      "com.peknight" %%% "codec-circe" % pekCodecVersion,
      "com.peknight" %%% "http4s-ext" % pekExtVersion,
    ),
  )

val pekVersion = "0.1.0-SNAPSHOT"
val pekCodecVersion = pekVersion
val pekExtVersion = pekVersion