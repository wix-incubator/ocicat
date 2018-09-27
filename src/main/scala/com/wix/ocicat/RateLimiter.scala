package com.wix.ocicat

import cats.effect._
import cats.effect.concurrent.{Deferred, Ref, Semaphore}
import cats.implicits._
import cats.effect.implicits._

import scala.collection.immutable
//import scala.language.postfixOps

trait RateLimiter[F[_]] {
  def submit[A](f: F[A]): F[Unit]
  def await[A](f: F[A]): F[A]
}

object RateLimiter {
  def unsafeCreate[F[_] : Effect : Concurrent](rate: Rate, timer: Timer[F], maxPending: Long): RateLimiter[F] =
    Effect[F].toIO(apply(rate, timer, maxPending)).unsafeRunSync()

  def apply[F[_] : Concurrent](rate: Rate, timer: Timer[F], maxPending: Long): F[RateLimiter[F]] = for {
    q <- Queue[F, F[_]](maxPending)
    throttler <- Throttler[F, Int](rate, timer.clock)
    _ <- {
      {
        for {
          _ <- throttler.await(0)
          _ <- throttler.throttle(0)
          a <- q.dequeue
          _ <- a.start.void
        } yield ()
      }.attempt.iterateWhile { _ => true }
    }.start
  } yield new RateLimiter[F] {

    override def submit[A](f: F[A]): F[Unit] = q.enqueue(f)

    override def await[A](f: F[A]): F[A] = for {
      p <- Deferred[F, Either[Throwable, A]]
      a <- q.enqueue(f.attempt.flatTap(p.complete)) *> p.get.rethrow
    } yield a
  }
}

trait Queue[F[_], A] {
  def enqueue(a: A): F[Unit]

  def dequeue: F[A]
}

object Queue {
  def apply[F[_] : Concurrent, A](capacity: Long): F[Queue[F, A]] = {
    for {
      emptyLock <- Semaphore(0)
      fullLock <- Semaphore(capacity)
      queueRef <- Ref.of(immutable.Queue.empty[A])
    } yield new Queue[F, A] {

      override def enqueue(a: A): F[Unit] = for {
        _ <- fullLock.tryAcquire ifM(
          ifTrue = queueRef.update(queue => queue.enqueue(a)) <* emptyLock.release,
          ifFalse = Concurrent[F].raiseError[Unit](CapacityExceededException()))
      } yield {}

      override def dequeue: F[A] = {
        val dequeAndRelease = for {
          a <- queueRef.modify(queue => queue.dequeue.swap)
          _ <- fullLock.release
        } yield a

        emptyLock.acquire *> dequeAndRelease
      }
    }
  }
}

case class CapacityExceededException() extends RuntimeException("")