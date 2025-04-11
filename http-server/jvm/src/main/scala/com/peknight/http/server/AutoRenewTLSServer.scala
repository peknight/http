package com.peknight.http.server

import cats.data.NonEmptyList
import cats.effect.*
import cats.syntax.applicative.*
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import cats.syntax.option.*
import cats.syntax.order.*
import com.peknight.cats.instances.time.instant.given
import com.peknight.commons.time.syntax.temporal.-
import com.peknight.security.key.store.pkcs12
import com.peknight.security.provider.Provider
import fs2.Stream
import fs2.io.net.Network
import fs2.io.net.tls.TLSContext

import java.security.cert.X509Certificate
import java.security.{PrivateKey, Provider as JProvider}
import java.time.Instant
import scala.concurrent.duration.*

object AutoRenewTLSServer:
  private def check[F[_]: Sync](certificate: X509Certificate, threshold: FiniteDuration)
                       (fetch: F[(NonEmptyList[X509Certificate], PrivateKey)])
  : F[Option[(NonEmptyList[X509Certificate], PrivateKey)]] =
      Option(certificate.getNotAfter).map(_.toInstant) match
        case Some(notAfter) =>
          Clock[F].realTimeInstant.flatMap {
            case now if now >= notAfter - threshold => fetch.map(_.some)
            case _ => none.pure[F]
          }
        case _ => none.pure[F]

  private def allocated[F[_]: Async, Server](certificates: NonEmptyList[X509Certificate], key: PrivateKey,
                                             alias: String, keyPassword: String, provider: Option[Provider | JProvider])
                                            (builder: TLSContext[F] => Resource[F, Server])
  : F[(Server, F[Unit])] =
    for
      keyStore <- pkcs12[F](alias, key, keyPassword, certificates, provider)
      tlsContext <- Network.forAsync[F].tlsContext.fromKeyStore(keyStore, keyPassword.toCharArray)
      (server, release) <- builder(tlsContext).allocated[Server]
    yield
      (server, release)

  def apply[F[_]: Async, Server](scheduler: Stream[F, ?], threshold: FiniteDuration = 7.days,
                                 alias: String = "", keyPassword: String = "",
                                 provider: Option[Provider | JProvider] = None)
                                (fetch: F[(NonEmptyList[X509Certificate], PrivateKey)])
                                (builder: TLSContext[F] => Resource[F, Server]): Resource[F, Server] =
    Resource.make[F, (Server, F[Unit])] {
      for
        (certificates, key) <- fetch
        (server, release) <- allocated[F, Server](certificates, key, alias, keyPassword, provider)(builder)
        res <- scheduler.zipRight(Stream.unfoldEval[F, (X509Certificate, Server, F[Unit]), (Server, F[Unit])](
          (certificates.head, server, release)
        ) { case (certificate, server, release) =>
          for
            option <- check[F](certificate, threshold)(fetch)
            tuple <- option match
              case Some((certificates, key)) =>
                for
                  _ <- release
                  (server, release) <- allocated[F, Server](certificates, key, alias, keyPassword, provider)(builder)
                yield
                  ((server, release), (certificates.head, server, release))
              case _ => ((server, release), (certificate, server, release)).pure[F]
          yield
            tuple.some
        }).compile.last
      yield
        res.getOrElse((server, release))
    } { case (server, release) => release }.map(_._1)

end AutoRenewTLSServer