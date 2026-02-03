package com.peknight.http.client

import cats.Monad
import cats.data.EitherT
import cats.effect.Async
import cats.syntax.applicative.*
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
  def download[F[_]](request: Request[F], directory: Option[Path] = None, fileName: Option[Path] = None,
                     overwrite: Boolean = true, maxRedirects: Int = 5)
                    (redirect: (Request[F], Response[F]) => Option[Request[F]] = redirectByLocation[F])
                    (observe: Response[F] => Pipe[F, Byte, Nothing] = (response: Response[F]) => (in: Stream[F, Byte]) => in.drain)
                    (using Client[F])(using Async[F], Files[F])
  : EitherT[F, Error, Unit] =
    type G[X] = EitherT[F, Error, X]
    for
      path <- path(request, directory, fileName).toRight(OptionEmpty.label("fileName")).eLiftET[F]
      _ <- Monad[G].ifM[Unit](if overwrite then false.pure[G] else Files[F].exists(path).asET)(
        ().rLiftET,
        for
          _ <- path.createParentDirectories[F]().asET
          part = Path(s"$path.part")
          _ <- bodyWithRedirects[F](request, maxRedirects)(redirect)(observe).through(Files[F].writeAll(part))
            .compile.drain.asET
          _ <- Files[F].move(part, path).asET
        yield
          ()
      )
    yield
      ()

end download
