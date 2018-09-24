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

  case object Throttled extends ThrottleStatus

  case object NonThrottled extends ThrottleStatus


  def apply[F[_], A](window: Int, limit: Int)(implicit T: Timer[F], S: Sync[F]): F[Throttler[F, A]] = {

    for {
      now <- T.clock.realTime(TimeUnit.MILLISECONDS)
      currentTick = now / window
      state <- Ref.of((currentTick, Map.empty[A, Int]))
    } yield {
      new Throttler[F, A] {

        override def throttle(key: A) = for {
          now <- T.clock.realTime(TimeUnit.MILLISECONDS)
          currentTick = now / window
          throttleStatus <- state.modify {
            case (previousTick, counts) =>
              // todo validate limit
              if (currentTick > previousTick) {
                ((now, Map(key -> 1)), NonThrottled)
              } else {
                counts.get(key) match {
                  case Some(count) => if (count + 1 > limit) {
                    ((previousTick, counts + (key -> (count + 1))), Throttled)
                  } else {
                    ((previousTick, counts + (key -> (count + 1))), NonThrottled)
                  }
                  case None => ((previousTick, counts + (key -> 1)), NonThrottled)
                }
              }
          }
          _ <- throttleStatus match {
            case Throttled => S.raiseError(new RuntimeException(s"key $key is throttled"))
            case NonThrottled => S.unit
          }
        } yield ()
      }
    }


  }
}