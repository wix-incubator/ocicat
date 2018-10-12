package com.wix.ocicat

import java.util.concurrent.TimeUnit

import cats.arrow.FunctionK
import cats.{ApplicativeError, ~>}
import cats.effect.concurrent.Ref
import cats.effect.{Clock, Effect, Sync}
import cats.implicits._

import scala.language.higherKinds

trait Throttler[F[_], A] {
  def throttle(key: A): F[Unit]
}

object Throttler {

  sealed trait ThrottleStatus

  case class Throttled(calls: Int) extends ThrottleStatus

  case object NonThrottled extends ThrottleStatus

  def unsafeCreate[F[_], A](config: Rate)(implicit E: Effect[F]): Throttler[F, A] = {
    E.toIO(apply[F, A](config, Clock.create)(E)).unsafeRunSync()
  }

  def create[F[_], A](config: Rate)(implicit S: Sync[F]): F[Throttler[F, A]] = {
    apply(config, Clock.create)(S)
  }

  def apply[F[_], A](config: Rate, clock: Clock[F])(implicit S: Sync[F]): F[Throttler[F, A]] = {
    apply0[F, F, A](config, clock, FunctionK.id)
  }

  def apply0[F[_] : Sync, G[_] : Sync, A](config: Rate, clock: Clock[F], nt: F ~> G): F[Throttler[G, A]] = {
    val windowMillis = config.window.toMillis
    val G = implicitly[Sync[G]]

    for {
      _ <- validateRate(config)(implicitly[ApplicativeError[F, Throwable]])
      now <- clock.realTime(TimeUnit.MILLISECONDS)
      currentTick = now / windowMillis
      state <- Ref.of[F, (Long, Map[A, Int])]((currentTick, Map.empty[A, Int]))
    } yield {
      new Throttler[G, A] {
        override def throttle(key: A): G[Unit] = for {
          now <- nt(clock.realTime(TimeUnit.MILLISECONDS))
          currentTick = now / windowMillis
          throttleStatus <- nt(state.modify { case (previousTick, counts) =>
            if (currentTick > previousTick) {
              ((currentTick, Map(key -> 1)), NonThrottled)
            } else {
              counts.get(key) match {
                case Some(count) =>
                  if (count + 1 > config.limit) {
                    ((previousTick, counts + (key -> (count + 1))), Throttled(count + 1))
                  } else {
                    ((previousTick, counts + (key -> (count + 1))), NonThrottled)
                  }
                case None => ((previousTick, counts + (key -> 1)), NonThrottled)
              }
            }
          })
          _ <- throttleStatus match {
            case Throttled(calls) => G.raiseError(new ThrottleException(key, calls, config))
            case NonThrottled => G.unit
          }
        } yield ()
      }
    }
  }


  private def validateRate[F[_]](r: Rate)(implicit AE: ApplicativeError[F, Throwable]): F[Unit] = {
    val validation = {
      if (r.limit <= 0) List(s"limit of calls should be greater than 0 but got ${r.limit}") else List()
    } ++ {
      if (r.window._1 <= 0) List(s"duration should be greater than 0 but got ${r.window._1}") else List()
    }

    val validationMessage = validation.foldSmash("throttle config is invalid : ", " and ", "")

    if (validation.isEmpty) {
      AE.unit
    } else {
      AE.raiseError(new InvalidRateException(validationMessage))
    }
  }

}

class InvalidRateException(msg: String) extends RuntimeException(msg)

class ThrottleException(key: Any, calls: Int, config: Rate) extends RuntimeException(s"$key is throttled after $calls calls, config is $config")