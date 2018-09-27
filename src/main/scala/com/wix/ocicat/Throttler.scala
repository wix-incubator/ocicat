package com.wix.ocicat

import java.util.concurrent.TimeUnit

import cats.ApplicativeError
import cats.effect.concurrent.{Deferred, Ref}
import cats.effect._
import cats.implicits._

import scala.language.higherKinds

trait Throttler[F[_], A] {
  def throttle(key: A): F[Unit]

  def await(key: A): F[Unit]
}

object Throttler {

  sealed trait ThrottleStatus[F[_]]

  case class Throttled[F[_]](calls: Int) extends ThrottleStatus[F]

  case class NonThrottled[F[_]](callback: Option[Deferred[F, Unit]]) extends ThrottleStatus[F]

  def unsafeCreate[F[_], A](config: Rate)(implicit E: ConcurrentEffect[F]): Throttler[F, A] = {
    E.toIO(apply[F, A](config, Clock.create)(E)).unsafeRunSync()
  }

  def create[F[_], A](config: Rate)(implicit S: Concurrent[F]): F[Throttler[F, A]] = {
    apply(config, Clock.create)(S)
  }

  case class ThrottlerEntry[F[_]](calls: Int, callback: Deferred[F, Unit])

  def apply[F[_], A](config: Rate, clock: Clock[F])(implicit C: Concurrent[F]): F[Throttler[F, A]] = {
    val windowMillis = config.window.toMillis

    for {
      _ <- validateRate(config)(implicitly[ApplicativeError[F, Throwable]])
      now <- clock.realTime(TimeUnit.MILLISECONDS)
      currentTick = now / windowMillis
      state <- Ref.of((currentTick, Map.empty[A, ThrottlerEntry[F]]))
      _ <- clean(state)
    } yield {
      new Throttler[F, A] {

        override def throttle(key: A): F[Unit] = for {
          now <- clock.realTime(TimeUnit.MILLISECONDS)
          currentTick = now / windowMillis
          p <- Deferred[F, Unit]
          throttleStatus <- state.modify { case (previousTick, counts) =>

            if (currentTick > previousTick) {
              counts.get(key) match {
                case Some(ThrottlerEntry(_, callback)) => ((currentTick, Map(key -> ThrottlerEntry[F](1, p))), NonThrottled[F](Some(callback)))
                case None => ((currentTick, Map(key -> ThrottlerEntry[F](1, p))), NonThrottled[F](None))
              }
            } else {
              counts.get(key) match {
                case Some(ThrottlerEntry(count, callback)) =>
                  if (count + 1 > config.limit) {
                    ((previousTick, counts + (key -> ThrottlerEntry[F](count + 1, callback))), Throttled[F](count + 1))
                  } else {
                    ((previousTick, counts + (key -> ThrottlerEntry[F](count + 1, callback))), NonThrottled[F](None))
                  }
                case None => ((previousTick, counts + (key -> ThrottlerEntry[F](1, p))), NonThrottled[F](None))
              }
            }
          }
          _ <- throttleStatus match {
            case Throttled(calls) => C.raiseError(new ThrottleException(key, calls, config))
            case NonThrottled(Some(callback)) => callback.complete(())
            case NonThrottled(None) => C.unit
          }
        } yield ()

        override def await(key: A) = {
          for {
            counts <- state.get
            _ <- {
              counts._2.get(key) match {
                case Some(ThrottlerEntry(_, callback)) => callback.get
                case None => Concurrent[F].unit
              }
            }
          } yield ()
        }

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