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
      state <- Ref.of((now, Map.empty[A, Int]))
    } yield {
      new Throttler[F, A] {

        override def throttle(key: A) = for {
          now <- T.clock.realTime(TimeUnit.MILLISECONDS)
          throttleStatus <- state.modify {
            case (before, counts) =>
              // todo normalize window
              // todo validate limit
              if ((now - before) > window) {
                ((now, Map(key -> 1)), NonThrottled)
              } else {
                counts.get(key) match {
                  case Some(count) => if (count + 1 > limit) {
                    ((before, counts + (key -> (count + 1))), Throttled)
                  } else {
                    ((before, counts + (key -> (count + 1))), NonThrottled)
                  }
                  case None => ((before, counts + (key -> 1)), NonThrottled)
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