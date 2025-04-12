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
import com.peknight.error.syntax.applicativeError.asError
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
  private[server] case class State[F[_], Server](certificates: NonEmptyList[X509Certificate], key: PrivateKey,
                                                 server: Server, release: F[Unit])
  private def renew[F[_]: Sync](certificate: X509Certificate, threshold: FiniteDuration)
                               (fetch: F[(NonEmptyList[X509Certificate], PrivateKey)])
  : F[Option[(NonEmptyList[X509Certificate], PrivateKey)]] =
      Option(certificate.getNotAfter).map(_.toInstant) match
        case Some(notAfter) =>
          Clock[F].realTimeInstant.flatMap {
            case now if now >= notAfter - threshold => fetch.asError.map(_.toOption)
            case _ => none.pure[F]
          }
        case _ => none.pure[F]

  private def allocated[F[_] : Async, Server](certificates: NonEmptyList[X509Certificate], key: PrivateKey,
                                              alias: String, keyPassword: String, provider: Option[Provider | JProvider])
                                             (builder: TLSContext[F] => Resource[F, Server])
  : F[(Server, F[Unit])] =
    for
      keyStore <- pkcs12[F](alias, key, keyPassword, certificates, provider)
      tlsContext <- Network.forAsync[F].tlsContext.fromKeyStore(keyStore, keyPassword.toCharArray)
      (server, release) <- builder(tlsContext).allocated[Server]
    yield
      (server, release)

  private def run[F[_]: Async, Server](state: Option[State[F, Server]], threshold: FiniteDuration,
                                       alias: String, keyPassword: String,
                                       provider: Option[Provider | JProvider])
                                      (fetch: Boolean => F[(NonEmptyList[X509Certificate], PrivateKey)])
                                      (builder: TLSContext[F] => Resource[F, Server])
  : F[State[F, Server]] =
    state match
      case Some(state @ State(certificates, key, server, release)) =>
        renew[F](certificates.head, threshold)(fetch(false)).flatMap {
          case Some((certificates, key)) =>
            for
              _ <- release
              (server, release) <- allocated[F, Server](certificates, key, alias, keyPassword, provider)(builder)
            yield
              State(certificates, key, server, release)
          case _ => state.pure[F]
        }
      case _ =>
        for
          (certificates, key) <- fetch(true)
          (certificates, key) <- renew[F](certificates.head, threshold)(fetch(false)).map(_.getOrElse((certificates, key)))
          (server, release) <- allocated[F, Server](certificates, key, alias, keyPassword, provider)(builder)
        yield
          State(certificates, key, server, release)

  def apply[F[_]: Async, Server](scheduler: Stream[F, ?], threshold: FiniteDuration = 7.days,
                                 alias: String = "", keyPassword: String = "",
                                 provider: Option[Provider | JProvider] = None)
                                // def fetch(isInit: Boolean): F[(NonEmptyList[X509Certificate], PrivateKey)]
                                (fetch: Boolean => F[(NonEmptyList[X509Certificate], PrivateKey)])
                                (builder: TLSContext[F] => Resource[F, Server]): Resource[F, Server] =
    Resource.make[F, (Server, F[Unit])] {
      for
        init <- run[F, Server](None, threshold, alias, keyPassword, provider)(fetch)(builder)
        res <- scheduler.zipRight(Stream.unfoldEval[F, State[F, Server], (Server, F[Unit])](init) { state =>
          for
            next <- run[F, Server](state.some, threshold, alias, keyPassword, provider)(fetch)(builder)
          yield
            ((next.server, next.release), next).some
        }).compile.last
      yield
        res.getOrElse((init.server, init.release))
    } { case (server, release) => release }.map(_._1)

end AutoRenewTLSServer