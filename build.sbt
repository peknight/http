ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.7.1"

ThisBuild / organization := "com.peknight"

ThisBuild / versionScheme := Some("early-semver")

ThisBuild / publishTo := {
  val nexus = "https://nexus.peknight.com/repository"
  if (isSnapshot.value)
    Some("snapshot" at s"$nexus/maven-snapshots/")
  else
    Some("releases" at s"$nexus/maven-releases/")
}

ThisBuild / credentials ++= Seq(
  Credentials(Path.userHome / ".sbt" / ".credentials")
)

ThisBuild / resolvers ++= Seq(
  "Pek Nexus" at "https://nexus.peknight.com/repository/maven-public/",
)

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
      "com.peknight" %%% "method-core" % pekMethodVersion,
      "com.peknight" %%% "api-codec-instances" % pekApiVersion,
      "com.peknight" %%% "commons-time" % pekCommonsVersion,
      "com.peknight" %%% "cats-instances-time" % pekInstancesVersion,
    ),
  )

val http4sVersion = "1.0.0-M34"
val pekVersion = "0.1.0-SNAPSHOT"
val pekCommonsVersion = pekVersion
val pekExtVersion = pekVersion
val pekInstancesVersion = pekVersion
val pekMethodVersion = pekVersion
val pekCodecVersion = pekVersion
val pekApiVersion = pekVersion
