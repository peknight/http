package com.peknight.http

import cats.Monad
import cats.data.EitherT
import cats.effect.{Async, MonadCancel}
import cats.syntax.applicative.*
import cats.syntax.either.*
import cats.syntax.functor.*
import cats.syntax.option.*
import com.peknight.cats.syntax.eitherT.{eLiftET, rLiftET}
import com.peknight.error.Error
import com.peknight.error.option.OptionEmpty
import com.peknight.error.syntax.applicativeError.{aeAsET, asET}
import fs2.io.file.{Files, Path}
import org.http4s.Method.GET
import org.http4s.Status.{Redirection, ResponseClass}
import org.http4s.client.Client
import org.http4s.headers.Location
import org.http4s.{Request, Response}

import scala.CanEqual.derived

package object client:
  private def redirectByLocation[F[_]](request: Request[F], response: Response[F]): Option[Request[F]] =
    response.headers.get[Location] match
      case Some(location) => Request(GET, request.uri.resolve(location.uri)).some
      case _ => None

  def runWithRedirects[F[_], A](request: Request[F], maxRedirects: Int = 5)
                               (decode: Response[F] => F[A])
                               (redirect: (Request[F], Response[F]) => Option[Request[F]])
                               (using client: Client[F])(using MonadCancel[F, Throwable])
  : EitherT[F, Error, A] =
    type G[X] = EitherT[F, Error, X]
    Monad[G].tailRecM[(Request[F], Int), A]((request, maxRedirects)) {
      case (request, maxRedirects) => client.run(request).use { response =>
        given CanEqual[ResponseClass, ResponseClass] = derived
        if response.status.responseClass == Redirection then
          if maxRedirects <= 0 then Error(response.status).asLeft[Either[(Request[F], Int), A]].pure[F]
          else redirect(request, response) match
            case Some(request) => (request, maxRedirects - 1).asLeft[A].asRight[Error].pure[F]
            case _ => OptionEmpty.label("Location").asLeft[Either[(Request[F], Int), A]].pure[F]
        else if response.status.isSuccess then
          decode(response).map(_.asRight[(Request[F], Int)].asRight[Error])
        else
          Error(response.status).asLeft[Either[(Request[F], Int), A]].pure[F]
      }.aeAsET
    }

  def downloadIfNotExists[F[_]](request: Request[F], filePath: Option[Path] = None, maxRedirects: Int = 5)
                               (redirect: (Request[F], Response[F]) => Option[Request[F]])
                               (using Client[F])(using Async[F], Files[F])
  : EitherT[F, Error, Unit] =
    type G[X] = EitherT[F, Error, X]
    for
      path <- filePath.orElse(request.uri.path.segments.lastOption.map(segment => Path(segment.toString)))
        .toRight(OptionEmpty.label("filePath")).eLiftET[F]
      _ <- Monad[G].ifM[Unit](Files[F].exists(path).asET)(
        ().rLiftET,
        runWithRedirects[F, Unit](request, maxRedirects)(_.body.through(Files[F].writeAll(path)).compile.drain)(redirect)
      )
    yield
      ()
end client
