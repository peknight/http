import com.peknight.build.gav.*
import com.peknight.build.sbt.*

commonSettings

lazy val http = (project in file("."))
  .settings(name := "http")
  .aggregate(httpCore.projectRefs *)
  .aggregate(httpClient.projectRefs *)

lazy val httpCore = (projectMatrix in file("http-core"))
  .settings(name := "http-core")
  .settings(libraryDependencies ++= dependencies(
    peknight.codec.http4s,
    peknight.codec.circe,
    peknight.http4s,
    peknight.method,
    peknight.api.instances.codec,
    peknight.commons.time,
  ))
  .jvmPlatform(scalaVersions = Seq(scala.scala3.version))
  .jsPlatform(scalaVersions = Seq(scala.scala3.version))

lazy val httpClient = (projectMatrix in file("http-client"))
  .settings(name := "http-client")
  .settings(libraryDependencies ++= dependencies(
    peknight.fs2,
    peknight.fs2.io,
    peknight.error,
    http4s.client,
    fs2.io,
    typelevel.squants,
  ))
  .settings(libraryDependencies ++= testDependencies(
    http4s.ember.client,
    scalaTest.flatSpec,
    typelevel.catsEffect.testingScalaTest,
  ))
  .jvmPlatform(
    scalaVersions = Seq(scala.scala3.version),
    settings = Seq(
      libraryDependencies ++= jvmTestDependencies(logback.classic)
    )
  )
  .jsPlatform(scalaVersions = Seq(scala.scala3.version))
