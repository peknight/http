package com.peknight.http.syntax

import cats.data.{EitherT, StateT}
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

trait EitherTSyntax:
  extension [F[_], A](eitherT: EitherT[F, Error, HttpResponse[A]])
    def retryRandomState(f: (Either[Error, HttpResponse[A]], RetryState) => StateT[F, (Option[Instant], Random[F]), Retry])
                        (using Async[F], RandomProvider[F]): EitherT[F, Error, HttpResponse[A]] =
      EitherT(httpRetry.random(eitherT.value)(f))
    def retryRandom(maxAttempts: Option[Int] = Some(3),
                    timeout: Option[FiniteDuration] = None,
                    interval: Option[FiniteDuration] = Some(1.second),
                    offset: Option[Interval[FiniteDuration]] = None,
                    exponentialBackoff: Boolean = false)
                   (success: Either[Error, HttpResponse[A]] => Boolean)
                   (effect: (Either[Error, HttpResponse[A]], RetryState, Retry) => F[Unit])
                   (using Async[F], RandomProvider[F]): EitherT[F, Error, HttpResponse[A]] =
      EitherT(httpRetry.retryRandom(eitherT.value)(maxAttempts, timeout, interval, offset, exponentialBackoff)(success)(effect))
    def retry(maxAttempts: Option[Int] = Some(3),
              timeout: Option[FiniteDuration] = None,
              interval: Option[FiniteDuration] = Some(1.second),
              offset: Option[FiniteDuration] = None,
              exponentialBackoff: Boolean = false)
             (success: Either[Error, HttpResponse[A]] => Boolean)
             (effect: (Either[Error, HttpResponse[A]], RetryState, Retry) => F[Unit])
             (using Async[F]): EitherT[F, Error, HttpResponse[A]] =
      EitherT(httpRetry.retry(eitherT.value)(maxAttempts, timeout, interval, offset, exponentialBackoff)(success)(effect))
  end extension
end EitherTSyntax
object EitherTSyntax extends EitherTSyntax
