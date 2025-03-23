package com.peknight.http.method.retry.syntax

import cats.data.{EitherT, StateT}
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

trait EitherTSyntax:
  extension [F[_], A, B] (eitherT: EitherT[F, A, HttpResponse[B]])
    def random(f: (Either[Error, HttpResponse[B]], RetryState) => StateT[F, (Option[Instant], Random[F]), Retry])
              (using Async[F], RandomProvider[F]): EitherT[F, Error, HttpResponse[B]] =
      EitherT(httpRetry.random(eitherT.value)(f))
    def retryRandom(maxAttempts: Option[Int] = Some(3),
                    timeout: Option[FiniteDuration] = None,
                    interval: Option[FiniteDuration] = Some(1.second),
                    offset: Option[Interval[FiniteDuration]] = None,
                    exponentialBackoff: Boolean = false)
                   (success: Either[Error, HttpResponse[B]] => Boolean)
                   (effect: (Either[Error, HttpResponse[B]], RetryState, Retry) => F[Unit])
                   (using Async[F], RandomProvider[F]): EitherT[F, Error, HttpResponse[B]] =
      EitherT(httpRetry.retryRandom(eitherT.value)(maxAttempts, timeout, interval, offset, exponentialBackoff)(success)(effect))
    def retry(maxAttempts: Option[Int] = Some(3),
              timeout: Option[FiniteDuration] = None,
              interval: Option[FiniteDuration] = Some(1.second),
              offset: Option[FiniteDuration] = None,
              exponentialBackoff: Boolean = false)
             (success: Either[Error, HttpResponse[B]] => Boolean)
             (effect: (Either[Error, HttpResponse[B]], RetryState, Retry) => F[Unit])
             (using Async[F]): EitherT[F, Error, HttpResponse[B]] =
      EitherT(httpRetry.retry(eitherT.value)(maxAttempts, timeout, interval, offset, exponentialBackoff)(success)(effect))
  end extension
end EitherTSyntax
object EitherTSyntax extends EitherTSyntax
