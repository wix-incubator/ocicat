package com.wix.ocicat

import java.util.concurrent.TimeUnit
import cats.effect.concurrent.Ref
import cats.effect.{Sync, Timer}
import cats.implicits._
import scala.language.higherKinds

trait Throttler[F[_], A] {
  def throttle(key: A): F[Unit]
}

object Throttler {

  sealed trait ThrottleStatus

  case class Throttled(calls: Int) extends ThrottleStatus

  case object NonThrottled extends ThrottleStatus


  def apply[F[_], A](config: ThrottleConfig)(implicit T: Timer[F], S: Sync[F]): F[Throttler[F, A]] = {
    val windowMillis = config.window.toMillis

    for {
      now <- T.clock.realTime(TimeUnit.MILLISECONDS)
      currentTick = now / windowMillis
      state <- Ref.of((currentTick, Map.empty[A, Int]))
    } yield {
      new Throttler[F, A] {

        override def throttle(key: A) = for {
          now <- T.clock.realTime(TimeUnit.MILLISECONDS)
          currentTick = now / windowMillis
          throttleStatus <- state.modify { case (previousTick, counts) =>
            // todo validate limit
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
          }
          _ <- throttleStatus match {
            case Throttled(calls) => S.raiseError(new ThrottleException(key, calls, config))
            case NonThrottled => S.unit
          }
        } yield ()
      }
    }


  }
}

class ThrottleException(key: Any, calls: Int, config: ThrottleConfig) extends RuntimeException(s"$key is throttled after $calls calls, config is $config")