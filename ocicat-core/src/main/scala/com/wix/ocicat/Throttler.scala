package com.wix.ocicat

import cats.arrow.FunctionK
import cats.{ApplicativeError, ~>}
import cats.effect.{Clock, Effect, Sync}
import cats.implicits._
import com.wix.ocicat.storage.ThrottlerStorage
import com.wix.ocicat.storage.InMemoryStorage

import scala.language.higherKinds

trait Throttler[F[_], A] {
  def throttle(key: A): F[Unit]
}

object Throttler {

  sealed trait ThrottleStatus

  case class Throttled(calls: Int) extends ThrottleStatus

  case object NonThrottled extends ThrottleStatus

  def unsafeCreate[F[_], A](config: Rate)(implicit E: Effect[F]): Throttler[F, A] = {
    E.toIO(apply[F, A](config, InMemoryStorage[F, A](Clock.create))(E)).unsafeRunSync()
  }

  def create[F[_], A](config: Rate)(implicit S: Sync[F]): F[Throttler[F, A]] = {
    apply(config, InMemoryStorage[F, A](Clock.create))(S)
  }

  def apply[F[_], A](config: Rate, st: ThrottlerStorage[F, A])(implicit S: Sync[F]): F[Throttler[F, A]] = {
    apply0[F, F, A](config, st, FunctionK.id)
  }

  def apply0[F[_] : Sync, G[_] : Sync, A](config: Rate, st: ThrottlerStorage[F, A], nt: F ~> G): F[Throttler[G, A]] = {
    val G = implicitly[Sync[G]]
    for {
      _ <- validateRate(config)(implicitly[ApplicativeError[F, Throwable]])
    } yield {
      new Throttler[G, A] {
        override def throttle(key: A): G[Unit] = for {
          capacity <- nt(st.incrementAndGet(key, config))
          throttleStatus = if (capacity.counts > config.limit) Throttled(capacity.counts) else NonThrottled
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
