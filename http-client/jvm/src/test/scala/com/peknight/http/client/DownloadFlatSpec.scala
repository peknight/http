package com.peknight.http.client

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.http4s.Method.GET
import org.http4s.Uri
import org.http4s.client.dsl.io.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.syntax.literals.uri
import org.scalatest.flatspec.AsyncFlatSpec

class DownloadFlatSpec extends AsyncFlatSpec with AsyncIOSpec:
  "Download" should "pass" in {
    val uri: Uri = uri"https://github.com/fatedier/frp/releases/download/v0.66.0/frp_0.66.0_linux_amd64.tar.gz"
    EmberClientBuilder.default[IO].withMaxResponseHeaderSize(16384).build
      .use(client => downloadIfNotExists[IO](GET(uri))(redirectByLocation)(using client).value)
      .map(either => assert(either.isRight))
  }
end DownloadFlatSpec
