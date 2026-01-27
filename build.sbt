import com.peknight.build.gav.*
import com.peknight.build.sbt.*

commonSettings

lazy val http = (project in file("."))
  .settings(name := "http")
  .aggregate(
    httpCore.jvm,
    httpCore.js,
    httpClient.jvm,
    httpClient.js,
  )

lazy val httpCore = (crossProject(JSPlatform, JVMPlatform) in file("http-core"))
  .settings(name := "http-core")
  .settings(crossDependencies(
    peknight.codec.http4s,
    peknight.codec.circe,
    peknight.http4s,
    peknight.method,
    peknight.api.instances.codec,
    peknight.commons.time,
  ))

lazy val httpClient = (crossProject(JSPlatform, JVMPlatform) in file("http-client"))
  .settings(name := "http-client")
  .settings(crossDependencies(
    peknight.fs2,
    peknight.error,
    http4s.client,
    fs2.io,
  ))
  .settings(crossTestDependencies(
    http4s.ember.client,
    scalaTest.flatSpec,
    typelevel.catsEffect.testingScalaTest,
  ))
  .settings(libraryDependencies ++= Seq(
    jvmTestDependency(logback.classic),
  ))
