package com.peknight.http.method

import cats.data.{EitherT, StateT}
import cats.effect.{Async, Clock, Sync}
import cats.syntax.eq.*
import cats.syntax.functor.*
import cats.syntax.option.*
import com.peknight.commons.time.syntax.instant.toDuration
import com.peknight.error.Error
import com.peknight.error.syntax.applicativeError.asError
import com.peknight.http.HttpResponse
import com.peknight.http4s.ext.syntax.headers.getRetryAfter
import com.peknight.method.retry.Retry.{MaxAttempts, Success, stateT}
import com.peknight.method.retry.{Retry, RetryState}
import com.peknight.random.Random
import com.peknight.random.provider.RandomProvider
import com.peknight.spire.ext.syntax.bound.{lower, upper}
import spire.math.Interval
import spire.math.interval.ValueBound

import java.time.Instant
import scala.concurrent.duration.*

package object retry:

  def random[F[_]: {Async, RandomProvider}, A](fe: F[Either[Error, HttpResponse[A]]])
                                              (f: (Either[Error, HttpResponse[A]], RetryState) => StateT[F, (Option[Instant], Random[F]), Retry])
  : F[Either[Error, HttpResponse[A]]] =
    val eitherT =
      for
        random <- EitherT(RandomProvider[F].random.asError)
        result <- EitherT(stateT[F, (Option[Instant], Random[F]), HttpResponse[A]](fe)(f).runA((None, random)))
      yield
        result
    eitherT.value

  def retryRandom[F[_]: {Async, RandomProvider}, A](fe: F[Either[Error, HttpResponse[A]]])
                                                   (maxAttempts: Option[Int] = Some(3),
                                                    timeout: Option[FiniteDuration] = None,
                                                    interval: Option[FiniteDuration] = Some(1.second),
                                                    offset: Option[Interval[FiniteDuration]] = None,
                                                    exponentialBackoff: Boolean = false)
                                                   (success: Either[Error, HttpResponse[A]] => Boolean)
                                                   (effect: (Either[Error, HttpResponse[A]], RetryState, Retry) => F[Unit])
  : F[Either[Error, HttpResponse[A]]] =
    random(fe)(randomOffset(maxAttempts, timeout, interval, offset, exponentialBackoff)(success)(effect))

  def retry[F[_]: Async, A](fe: F[Either[Error, HttpResponse[A]]])
                           (maxAttempts: Option[Int] = Some(3),
                            timeout: Option[FiniteDuration] = None,
                            interval: Option[FiniteDuration] = Some(1.second),
                            offset: Option[FiniteDuration] = None,
                            exponentialBackoff: Boolean = false)
                           (success: Either[Error, HttpResponse[A]] => Boolean)
                           (effect: (Either[Error, HttpResponse[A]], RetryState, Retry) => F[Unit])
  : F[Either[Error, HttpResponse[A]]] =
    stateT[F, (Option[Instant], Unit), HttpResponse[A]](fe)(fixedOffset(maxAttempts, timeout, interval, offset,
      exponentialBackoff)(success)(effect)).runA((None, ()))

  private def handleRetryAfterHeader[F[_]: Sync, S, A](either: Either[Error, HttpResponse[A]])
  : StateT[F, (Option[Instant], S), Option[FiniteDuration]] =
    def toDuration(retryAfter: Instant): StateT[F, (Option[Instant], S), Option[FiniteDuration]] =
      StateT.liftF[F, (Option[Instant], S), FiniteDuration](Clock[F].realTime.map(now => retryAfter.toDuration - now))
        .map(sleep => if sleep > 0.nano then sleep.some else none)
    either.toOption.map(_.headers).flatMap(_.getRetryAfter) match
      case Some(retryAfter) =>
        for
          sleep <- toDuration(retryAfter)
          _ <- sleep.fold(StateT.pure(()))(_ =>
            StateT.modify[F, (Option[Instant], S)]((_, s) => (retryAfter.some, s))
          )
        yield
          sleep
      case _ =>
        for
          (after, _) <- StateT.get[F, (Option[Instant], S)]
          sleep <- after.fold(StateT.pure(none[FiniteDuration]))(toDuration)
        yield
          sleep
  end handleRetryAfterHeader

  private def handleState[F[_]: Sync, S, A](maxAttempts: Option[Int] = Some(3),
                                            timeout: Option[FiniteDuration] = None,
                                            interval: Option[FiniteDuration] = Some(1.second),
                                            exponentialBackoff: Boolean = false)
                                           (success: Either[Error, HttpResponse[A]] => Boolean)
                                           (effect: (Either[Error, HttpResponse[A]], RetryState, Retry) => F[Unit])
                                           (offsetS: StateT[F, (Option[Instant], S), Option[FiniteDuration]])
  : (Either[Error, HttpResponse[A]], RetryState) => StateT[F, (Option[Instant], S), Retry] =
    (either, state) =>
      val stateT: StateT[F, (Option[Instant], S), Retry] =
        if success(either) then StateT.pure[F, (Option[Instant], S), Retry](Success)
        else if maxAttempts.exists(_ <= state.attempts) then StateT.pure(MaxAttempts(state.attempts))
        else
          for
            sleep <- handleRetryAfterHeader[F, S, A](either)
            retry <- sleep match
              case Some(sleep) => StateT.pure(Retry.After(sleep))
              case None =>
                Retry.handleInterval[F, (Option[Instant], S)](state, timeout, interval, exponentialBackoff)(offsetS)
          yield
            retry
      stateT.flatMap(retry => StateT.liftF(effect(either, state, retry)).as(retry))
  end handleState

  private def randomOffset[F[_]: Sync, A](maxAttempts: Option[Int] = Some(3),
                                          timeout: Option[FiniteDuration] = None,
                                          interval: Option[FiniteDuration] = Some(1.second),
                                          offset: Option[Interval[FiniteDuration]] = None,
                                          exponentialBackoff: Boolean = false)
                                         (success: Either[Error, HttpResponse[A]] => Boolean)
                                         (effect: (Either[Error, HttpResponse[A]], RetryState, Retry) => F[Unit])
  : (Either[Error, HttpResponse[A]], RetryState) => StateT[F, (Option[Instant], Random[F]), Retry] =
    handleState(maxAttempts, timeout, interval, exponentialBackoff)(success)(effect) {
      val (minOffset, maxOffset) = offsetInterval(offset)
      if minOffset < maxOffset then
        StateT[F, (Option[Instant], Random[F]), Option[FiniteDuration]]((retryAfter, random) =>
          random.between(minOffset.toNanos, (maxOffset + 1.nano).toNanos)
            .map((random, offset) => ((retryAfter, random), offset.nanos.some))
        )
      else if minOffset === maxOffset then StateT.pure(minOffset.some)
      else StateT.pure(none[FiniteDuration])
    }

  private def offsetInterval(offset: Option[Interval[FiniteDuration]]): (FiniteDuration, FiniteDuration) =
    offset match
      case Some(interval) =>
        val (l, u) = (interval.lowerBound, interval.upperBound) match
          case (lower: ValueBound[FiniteDuration], upper: ValueBound[FiniteDuration]) => (lower.lower, upper.upper)
          case (lower: ValueBound[FiniteDuration], _) => (lower.lower, lower.lower max 0.nano)
          case (_, upper: ValueBound[FiniteDuration]) => (upper.upper min 0.nano, upper.upper)
          case _ => (0.nano, 0.nano)
        if l <= u then (l, u) else (0.nano, 0.nano)
      case _ => (0.nano, 0.nano)

  private def fixedOffset[F[_]: Sync, A](maxAttempts: Option[Int] = Some(3),
                                         timeout: Option[FiniteDuration] = None,
                                         interval: Option[FiniteDuration] = Some(1.second),
                                         offset: Option[FiniteDuration] = None,
                                         exponentialBackoff: Boolean = false)
                                        (success: (Either[Error, HttpResponse[A]]) => Boolean)
                                        (effect: (Either[Error, HttpResponse[A]], RetryState, Retry) => F[Unit])
  : (Either[Error, HttpResponse[A]], RetryState) => StateT[F, (Option[Instant], Unit), Retry] =
    handleState[F, Unit, A](maxAttempts, timeout, interval, exponentialBackoff)(success)(effect)(StateT.pure(offset))
end retry