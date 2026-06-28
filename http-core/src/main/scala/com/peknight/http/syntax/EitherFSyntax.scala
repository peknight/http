package com.peknight.http.syntax

import cats.data.StateT
import cats.effect.Async
import com.peknight.error.Error
import com.peknight.http.HttpResponse
import com.peknight.http.method.retry as httpRetry
import com.peknight.method.retry.{Retry, RetryState}
import com.peknight.random.Random
import com.peknight.random.provider.RandomProvider
import spire.math.Interval

import java.time.Instant
import scala.concurrent.duration.*

trait EitherFSyntax:
  extension [F[_], A](fe: F[Either[Error, HttpResponse[A]]])
    def retryRandomState(f: (Either[Error, HttpResponse[A]], RetryState) => StateT[F, (Option[Instant], Random[F]), Retry])
                        (using Async[F], RandomProvider[F]): F[Either[Error, HttpResponse[A]]] =
      httpRetry.random(fe)(f)
    def retryRandom(maxAttempts: Option[Int] = Some(3),
                    timeout: Option[FiniteDuration] = None,
                    interval: Option[FiniteDuration] = Some(1.second),
                    offset: Option[Interval[FiniteDuration]] = None,
                    exponentialBackoff: Boolean = false)
                   (success: Either[Error, HttpResponse[A]] => Boolean)
                   (effect: (Either[Error, HttpResponse[A]], RetryState, Retry) => F[Unit])
                   (using Async[F], RandomProvider[F]): F[Either[Error, HttpResponse[A]]] =
      httpRetry.retryRandom(fe)(maxAttempts, timeout, interval, offset, exponentialBackoff)(success)(effect)
    def retry(maxAttempts: Option[Int] = Some(3),
              timeout: Option[FiniteDuration] = None,
              interval: Option[FiniteDuration] = Some(1.second),
              offset: Option[FiniteDuration] = None,
              exponentialBackoff: Boolean = false)
             (success: Either[Error, HttpResponse[A]] => Boolean)
             (effect: (Either[Error, HttpResponse[A]], RetryState, Retry) => F[Unit])
             (using Async[F]): F[Either[Error, HttpResponse[A]]] =
      httpRetry.retry(fe)(maxAttempts, timeout, interval, offset, exponentialBackoff)(success)(effect)
  end extension
end EitherFSyntax
object EitherFSyntax extends EitherFSyntax
