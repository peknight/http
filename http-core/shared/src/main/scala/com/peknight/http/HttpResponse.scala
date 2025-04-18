package com.peknight.http

import cats.Show
import cats.effect.Sync
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import com.peknight.cats.instances.time.instant.given
import com.peknight.generic.derivation.show
import com.peknight.http4s.ext.syntax.headers.getExpiration
import org.http4s.{EntityDecoder, Headers, Response, Status}

import java.time.Instant

case class HttpResponse[A](status: Status, headers: Headers, body: A, expiration: Option[Instant])
object HttpResponse:
  def fromResponse[F[_], A](response: Response[F])(using Sync[F], EntityDecoder[F, A]): F[HttpResponse[A]] =
    for
      body <- response.as[A]
      expiration <- response.headers.getExpiration[F]
    yield
      HttpResponse(response.status, response.headers, body, expiration)
  given showHttpResponse[A](using Show[A]): Show[HttpResponse[A]] = show.derived[HttpResponse[A]]
end HttpResponse
