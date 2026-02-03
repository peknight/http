package com.peknight.http.client

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.syntax.option.*
import com.peknight.http.client.download.downloadIfNotExists
import fs2.io.file.Path
import org.http4s.Method.GET
import org.http4s.Uri
import org.http4s.client.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.syntax.literals.uri
import org.scalatest.flatspec.AsyncFlatSpec

class DownloadFlatSpec extends AsyncFlatSpec with AsyncIOSpec:
  "Download" should "pass" in {
    val uri: Uri = uri"https://nexus.peknight.com/repository/maven-public/org/typelevel/cats-core_3/2.13.0/cats-core_3-2.13.0.pom"
    EmberClientBuilder.default[IO].withMaxResponseHeaderSize(16384).build
      .use(client => downloadIfNotExists[IO](GET(uri), Path("test").some)()(showProgressInConsole[IO])(using client).value)
      .map(either => assert(either.isRight))
  }
end DownloadFlatSpec
