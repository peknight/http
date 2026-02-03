package com.peknight.http

import cats.Monad
import cats.data.EitherT
import cats.effect.std.Console
import cats.effect.{Async, MonadCancel}
import cats.syntax.applicative.*
import cats.syntax.either.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.monadError.*
import cats.syntax.option.*
import com.peknight.cats.syntax.eitherT.{eLiftET, rLiftET}
import com.peknight.error.Error
import com.peknight.error.option.OptionEmpty
import com.peknight.error.syntax.applicativeError.{aeAsET, asET}
import com.peknight.fs2.syntax.stream.evalScanChunksInitLast
import fs2.io.file.{Files, Path}
import fs2.{Chunk, Stream}
import org.http4s.Method.GET
import org.http4s.Status.{Redirection, ResponseClass}
import org.http4s.client.Client
import org.http4s.headers.Location
import org.http4s.{Request, Response}

import scala.CanEqual.derived

package object client:
  private given CanEqual[ResponseClass, ResponseClass] = derived

  private def redirectByLocation[F[_]](request: Request[F], response: Response[F]): Option[Request[F]] =
    response.headers.get[Location] match
      case Some(location) => Request(GET, request.uri.resolve(location.uri)).some
      case _ => None

  def runWithRedirects[F[_], A](request: Request[F], maxRedirects: Int = 5)
                               (decode: (Response[F], F[Unit]) => F[A])
                               (redirect: (Request[F], Response[F]) => Option[Request[F]] = redirectByLocation[F])
                               (using client: Client[F])(using MonadCancel[F, Throwable])
  : EitherT[F, Error, A] =
    type G[X] = EitherT[F, Error, X]
    Monad[G].tailRecM[(Request[F], Int), A]((request, maxRedirects)) {
      case (request, maxRedirects) => client.run(request).allocated.flatMap { (response, release) =>
        if response.status.responseClass == Redirection then
          if maxRedirects <= 0 then release.as(Error(response.status).asLeft[Either[(Request[F], Int), A]])
          else redirect(request, response) match
            case Some(request) => release.as((request, maxRedirects - 1).asLeft[A].asRight[Error])
            case _ => release.as(OptionEmpty.label("Location").asLeft[Either[(Request[F], Int), A]])
        else if response.status.isSuccess then
          decode(response, release).map(_.asRight[(Request[F], Int)].asRight[Error])
        else
          release.as(Error(response.status).asLeft[Either[(Request[F], Int), A]])
      }.aeAsET
    }

  def bodyWithRedirects[F[_]](request: Request[F], maxRedirects: Int = 5)
                             (redirect: (Request[F], Response[F]) => Option[Request[F]] = redirectByLocation[F])
                             (using client: Client[F])(using MonadCancel[F, Throwable]): Stream[F, Byte] =
    Stream.eval[F, Stream[F, Byte]](runWithRedirects[F, Stream[F, Byte]](request, maxRedirects)((response, release) =>
      response.body.onFinalize(release).pure[F])(redirect).value.rethrow).flatten

  def downloadIfNotExists[F[_]](request: Request[F], filePath: Option[Path] = None, maxRedirects: Int = 5)
                               (redirect: (Request[F], Response[F]) => Option[Request[F]] = redirectByLocation[F])
                               (body: Response[F] => Stream[F, Byte] = (response: Response[F]) => response.body)
                               (using Client[F])(using Async[F], Files[F])
  : EitherT[F, Error, Unit] =
    type G[X] = EitherT[F, Error, X]
    for
      path <- filePath.orElse(request.uri.path.segments.lastOption.map(segment => Path(segment.toString)))
        .toRight(OptionEmpty.label("filePath")).eLiftET[F]
      _ <- Monad[G].ifM[Unit](Files[F].exists(path).asET)(
        ().rLiftET,
        runWithRedirects[F, Unit](request, maxRedirects)((response, release) => body(response)
          .through(Files[F].writeAll(path))
          .onFinalize(release)
          .compile.drain)(redirect)
      )
    yield
      ()

  def downloadIfNotExistsWithConsole[F[_]](request: Request[F], filePath: Option[Path] = None, maxRedirects: Int = 5)
                                          (redirect: (Request[F], Response[F]) => Option[Request[F]] = redirectByLocation[F])
                                          (using Client[F])(using Async[F], Files[F], Console[F])
  : EitherT[F, Error, Unit] =
    def show(bytes: Long, chunk: Chunk[Byte], response: Response[F]): (Long, String) =
      val next = bytes + chunk.size
      (next, s"\r$next Bytes${response.contentLength.map(contentLength => s" / $contentLength Bytes = ${next * 100 / contentLength}%").getOrElse("")}")
    downloadIfNotExists[F](request, filePath, maxRedirects)(redirect)(response =>
      response.body.evalScanChunksInitLast[F, Byte, Byte, Long](0L) { (bytes, chunk) =>
        val (next, message) = show(bytes, chunk, response)
        Console[F].print(message).as((next, chunk))
      } { (bytes, chunk) =>
        val (next, message) = show(bytes, chunk, response)
        Console[F].println(message).as(chunk)
      }
    )
end client
