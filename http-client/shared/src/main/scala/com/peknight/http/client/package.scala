package com.peknight.http

import cats.data.EitherT
import cats.effect.std.Console
import cats.effect.{Async, MonadCancel}
import cats.syntax.applicative.*
import cats.syntax.either.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.monadError.*
import cats.syntax.show.*
import cats.{Applicative, Monad, Show}
import com.peknight.error.Error
import com.peknight.error.option.OptionEmpty
import com.peknight.error.syntax.applicativeError.aeAsET
import com.peknight.fs2.pipe.evalScanChunks
import fs2.io.file.Path
import fs2.{Chunk, Pipe, Stream}
import org.http4s.Status.{Redirection, ResponseClass}
import org.http4s.client.Client
import org.http4s.headers.Location
import org.http4s.{Request, Response}
import squants.information.*

import scala.CanEqual.derived

package object client:
  private given CanEqual[ResponseClass, ResponseClass] = derived

  def runWithRedirects[F[_], A](request: Request[F], maxRedirects: Int = 5)
                               (decode: Response[F] => F[A])
                               (redirect: (Request[F], Response[F]) => Option[Request[F]] = redirectByLocation[F])
                               (using client: Client[F])(using MonadCancel[F, Throwable])
  : EitherT[F, Error, A] =
    type G[X] = EitherT[F, Error, X]
    Monad[G].tailRecM[(Request[F], Int), A]((request, maxRedirects))((req, redirects) => client.run(req).use(
      resp => tailRecRedirect[F, A](req, resp, redirects)(decode)(redirect)
    ).aeAsET)

  def bodyWithRedirects[F[_]](request: Request[F], maxRedirects: Int = 5)
                             (redirect: (Request[F], Response[F]) => Option[Request[F]] = redirectByLocation[F])
                             (observe: Response[F] => Pipe[F, Byte, Nothing] = (response: Response[F]) => (in: Stream[F, Byte]) => in.drain)
                             (using client: Client[F])(using Async[F]): Stream[F, Byte] =
    type G[X] = EitherT[F, Error, X]
    Stream.eval[F, Stream[F, Byte]](Monad[G].tailRecM[(Request[F], Int), Stream[F, Byte]]((request, maxRedirects))(
      (req, redirects) => client.run(req).allocated.flatMap((resp, release) =>
        tailRecRedirect[F, Stream[F, Byte]](req, resp, redirects)(
          _.body.observe(observe(resp)).onFinalize(release).pure[F]
        )(redirect).flatMap {
          case r @ Right(Right(_)) => r.pure[F]
          case e => release.as(e)
        }
      ).aeAsET
    ).value.rethrow).flatten

  private def tailRecRedirect[F[_], A](request: Request[F], response: Response[F], redirects: Int)
                                      (decode: Response[F] => F[A])
                                      (redirect: (Request[F], Response[F]) => Option[Request[F]])
                                      (using Applicative[F]): F[Either[Error, Either[(Request[F], Int), A]]] =
    if response.status.responseClass == Redirection then
      if redirects <= 0 then Error(response.status).asLeft[Either[(Request[F], Int), A]].pure[F]
      else redirect(request, response) match
        case Some(request) => (request, redirects - 1).asLeft[A].asRight[Error].pure[F]
        case _ => OptionEmpty.label("Location").asLeft[Either[(Request[F], Int), A]].pure[F]
    else if response.status.isSuccess then
      decode(response).map(_.asRight[(Request[F], Int)].asRight[Error])
    else
      Error(response.status).asLeft[Either[(Request[F], Int), A]].pure[F]

  private def redirectByLocation[F[_]](request: Request[F], response: Response[F]): Option[Request[F]] =
    response.headers.get[Location].map(location => Request(uri = request.uri.resolve(location.uri)))

  private def path[F[_]](request: Request[F], directory: Option[Path], fileName: Option[Path]): Option[Path] =
    fileName.orElse(request.uri.path.segments.lastOption.map(segment => Path(segment.toString)))
      .map(filePath => directory.map(_ / filePath).getOrElse(filePath))

  def showProgressInConsole[F[_]: {Monad, Console}](response: Response[F]): Pipe[F, Byte, Nothing] =
    val total: Option[Information] = response.contentLength.filter(_ > 0).map(Bytes.apply)
    given Show[Information] with
      def show(t: Information): String =
        if t > Yottabytes(1) then t.in(Yottabytes).toString
        else if t > Zettabytes(1) then t.in(Zettabytes).toString
        else if t > Exabytes(1) then t.in(Exabytes).toString
        else if t > Petabytes(1) then t.in(Petabytes).toString
        else if t > Terabytes(1) then t.in(Terabytes).toString
        else if t > Gigabytes(1) then t.in(Gigabytes).toString
        else if t > Megabytes(1) then t.in(Megabytes).toString
        else if t > Kilobytes(1) then t.in(Kilobytes).toString
        else t.toBytes.toInt.toString
    end given
    in => in.through(evalScanChunks[F, F, Byte, Byte, Nothing, Information](Bytes(0)) { (acc, chunk) =>
      val next = acc + Bytes(chunk.size)
      Console[F].print(s"\r${next.show}${total.map(t => s" / ${t.show} = ${(next * 100 / t).toInt}%").getOrElse("")}").as((next, Chunk.empty))
    }).onFinalize[F](Console[F].println(""))
end client
