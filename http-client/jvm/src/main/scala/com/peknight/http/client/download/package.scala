package com.peknight.http.client

import cats.Monad
import cats.data.EitherT
import cats.effect.Async
import com.peknight.cats.syntax.eitherT.{eLiftET, rLiftET}
import com.peknight.error.Error
import com.peknight.error.option.OptionEmpty
import com.peknight.error.syntax.applicativeError.asET
import com.peknight.fs2.io.syntax.path.createParentDirectories
import fs2.io.file.{Files, Path}
import fs2.{Pipe, Stream}
import org.http4s.client.Client
import org.http4s.{Request, Response}

package object download:
  def download[F[_]](request: Request[F], directory: Option[Path] = None, fileName: Option[Path] = None, maxRedirects: Int = 5)
                    (redirect: (Request[F], Response[F]) => Option[Request[F]] = redirectByLocation[F])
                    (observe: Response[F] => Pipe[F, Byte, Nothing] = (response: Response[F]) => (in: Stream[F, Byte]) => in.drain)
                    (using Client[F])(using Async[F], Files[F])
  : EitherT[F, Error, Unit] =
    for
      path <- path(request, directory, fileName).toRight(OptionEmpty.label("fileName")).eLiftET[F]
      _ <- path.createParentDirectories[F]().asET
      _ <- bodyWithRedirects[F](request, maxRedirects)(redirect)(observe).through(Files[F].writeAll(path))
        .compile.drain.asET
    yield
      ()

  def downloadIfNotExists[F[_]](request: Request[F], directory: Option[Path] = None, fileName: Option[Path] = None, maxRedirects: Int = 5)
                               (redirect: (Request[F], Response[F]) => Option[Request[F]] = redirectByLocation[F])
                               (observe: Response[F] => Pipe[F, Byte, Nothing] = (response: Response[F]) => (in: Stream[F, Byte]) => in.drain)
                               (using Client[F])(using Async[F], Files[F])
  : EitherT[F, Error, Unit] =
    type G[X] = EitherT[F, Error, X]
    for
      path <- path(request, directory, fileName).toRight(OptionEmpty.label("fileName")).eLiftET[F]
      _ <- Monad[G].ifM[Unit](Files[F].exists(path).asET)(
        ().rLiftET,
        path.createParentDirectories[F]().asET
          .flatMap(_ => bodyWithRedirects[F](request, maxRedirects)(redirect)(observe).through(Files[F].writeAll(path))
            .compile.drain.asET
          )
      )
    yield
      ()

end download
