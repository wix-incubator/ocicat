package com.wix.ocicat

import java.util.concurrent.TimeUnit
import cats.ApplicativeError
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
      _ <- validateConfig(config)(implicitly[ApplicativeError[F, Throwable]])
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

  private def validateConfig[F[_]](config: ThrottleConfig)(implicit AE: ApplicativeError[F, Throwable]): F[Unit] = {
    val validation = {
      if (config.limit <= 0) List(s"limit of calls should be greater than 0 but got ${config.limit}") else List()
    } ++ {
      if (config.window._1 <= 0) List(s"duration should be greater than 0 but got ${config.window._1}") else List()
    }

    val validationMessage = validation.foldSmash("throttle config is invalid : ", " and ", "")

    if (validation.isEmpty) {
      AE.unit
    } else {
      AE.raiseError(new InvalidConfigException(validationMessage))
    }
  }

}

class InvalidConfigException(msg: String) extends RuntimeException(msg)

class ThrottleException(key: Any, calls: Int, config: ThrottleConfig) extends RuntimeException(s"$key is throttled after $calls calls, config is $config")