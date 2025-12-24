import com.peknight.build.gav.*
import com.peknight.build.sbt.*

commonSettings

lazy val http = (project in file("."))
  .settings(name := "http")
  .aggregate(
    httpCore.jvm,
    httpCore.js,
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
