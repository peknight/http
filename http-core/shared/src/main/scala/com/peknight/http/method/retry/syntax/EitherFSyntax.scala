package com.peknight.http.method.retry.syntax

import cats.data.StateT
import cats.effect.Async
import com.peknight.error.Error
import com.peknight.http.method.retry as httpRetry
import com.peknight.http.HttpResponse
import com.peknight.method.retry.{Retry, RetryState}
import com.peknight.random.Random
import com.peknight.random.provider.RandomProvider
import spire.math.Interval

import java.time.Instant
import scala.concurrent.duration.*

trait EitherFSyntax:
  extension [F[_], A, B] (fe: F[Either[A, HttpResponse[B]]])
    def random(f: (Either[Error, HttpResponse[B]], RetryState) => StateT[F, (Option[Instant], Random[F]), Retry])
              (using Async[F], RandomProvider[F]): F[Either[Error, HttpResponse[B]]] =
      httpRetry.random(fe)(f)
    def retryRandom(maxAttempts: Option[Int] = Some(3),
                    timeout: Option[FiniteDuration] = None,
                    interval: Option[FiniteDuration] = Some(1.second),
                    offset: Option[Interval[FiniteDuration]] = None,
                    exponentialBackoff: Boolean = false)
                   (success: Either[Error, HttpResponse[B]] => Boolean)
                   (effect: (Either[Error, HttpResponse[B]], RetryState, Retry) => F[Unit])
                   (using Async[F], RandomProvider[F]): F[Either[Error, HttpResponse[B]]] =
      httpRetry.retryRandom(fe)(maxAttempts, timeout, interval, offset, exponentialBackoff)(success)(effect)
    def retry(maxAttempts: Option[Int] = Some(3),
              timeout: Option[FiniteDuration] = None,
              interval: Option[FiniteDuration] = Some(1.second),
              offset: Option[FiniteDuration] = None,
              exponentialBackoff: Boolean = false)
             (success: Either[Error, HttpResponse[B]] => Boolean)
             (effect: (Either[Error, HttpResponse[B]], RetryState, Retry) => F[Unit])
             (using Async[F]): F[Either[Error, HttpResponse[B]]] =
      httpRetry.retry(fe)(maxAttempts, timeout, interval, offset, exponentialBackoff)(success)(effect)
  end extension
end EitherFSyntax
object EitherFSyntax extends EitherFSyntax
