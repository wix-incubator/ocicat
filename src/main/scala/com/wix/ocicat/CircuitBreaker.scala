package com.wix.ocicat

import java.util.concurrent.TimeUnit

import cats.effect.concurrent.Ref
import cats.effect.{Clock, Sync}
import cats.implicits._

import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds

trait CircuitBreaker[F[_]] {
  def protect[A](fa: F[A]): F[A]
}

object CircuitBreaker {

  sealed trait State

  case class Allowing(failures: Int) extends State

  case class Denying(expireAt: Long) extends State

  case class Probe(expireAt: Long) extends State

  class RejectedException extends RuntimeException

  def apply[F[_] : Sync](maxFailures: Int, resetTimeout: FiniteDuration, clock: Clock[F]): F[CircuitBreaker[F]] = {
    val resetMillis = resetTimeout.toMillis


    for {
      state <- Ref.of(Allowing(0): State)
    } yield {
      new CircuitBreaker[F] {

        val probeCallback: Either[Throwable, _] => F[Unit] = {
          case Right(_) => state.update {
            case Probe(_) => Allowing(0)
            case c => c
          }

          case Left(_) => for {
            time <- clock.monotonic(TimeUnit.MILLISECONDS)
            _ <- state.update {
              case Probe(_) => Denying(time + resetMillis)
              case c => c
            }
          } yield ()

        }

        val allowingCallback: Either[Throwable, _] => F[Unit] = {
          case Right(_) => state.update {
            case Allowing(_) => Allowing(0)
            case c => c
          }
          case Left(_) => for {
            time <- clock.monotonic(TimeUnit.MILLISECONDS)
            _ <- state.update {
              case Allowing(failures) if failures + 1 < maxFailures => Allowing(failures + 1)
              case Allowing(_) => Denying(time + resetMillis)
              case c => c
            }
          } yield ()
        }


        override def protect[A](fa: F[A]): F[A] = {
          val rejected = Sync[F].raiseError[A](new RejectedException)
          for {
            now <- clock.monotonic(TimeUnit.MILLISECONDS)
            task <- state.modify {
              case Denying(expireAt) if expireAt < now => (Probe(now + resetMillis), fa.attempt.flatTap(probeCallback).rethrow)
              case a: Allowing => (a, fa.attempt.flatTap(allowingCallback).rethrow)
              case Probe(expireAt) if expireAt < now => (Probe(now + resetMillis), fa.attempt.flatTap(probeCallback).rethrow)
              case d: Denying => (d, rejected)
              case p: Probe => (p, rejected)
            }
            result <- task
          } yield result
        }
      }
    }
  }

}


