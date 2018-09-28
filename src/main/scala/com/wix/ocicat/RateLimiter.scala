package com.wix.ocicat

import cats.effect._
import cats.effect.concurrent.{Deferred, Ref, Semaphore}
import cats.implicits._
import cats.effect.implicits._
import scala.collection.immutable

trait RateLimiter[F[_]] {
  def submit[A](f: F[A]): F[Unit]
  def await[A](f: F[A]): F[A]
}

object RateLimiter {
  def unsafeCreate[F[_] : Effect : Concurrent](rate: Rate, maxPending: Long, clock: Clock[F]): RateLimiter[F] =
    Effect[F].toIO(apply(rate, maxPending, clock)).unsafeRunSync()

  def apply[F[_] : Concurrent](rate: Rate, maxPending: Long, clock: Clock[F]): F[RateLimiter[F]] = for {
    q <- Queue[F, F[_]](maxPending)
    throttler <- Throttler[F, Int](rate, clock)
    _ <- {
      {
        for {
          _ <- q.await
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

  def await: F[Unit]
}

private [ocicat] object Queue {
  def apply[F[_] : Concurrent, A](capacity: Long): F[Queue[F, A]] = {
    for {
      emptyLock <- Semaphore(0)
      fullLock <- Semaphore(capacity)
      queueRef <- Ref.of(immutable.Queue.empty[A])
    } yield new Queue[F, A] {

      override def enqueue(a: A): F[Unit] = for {
        _ <- fullLock.tryAcquire ifM(
          ifTrue = queueRef.update(queue => queue.enqueue(a)) <* emptyLock.release,
          ifFalse = Concurrent[F].raiseError[Unit](TooManyPendingTasksException(capacity)))
      } yield {}

      override def dequeue: F[A] = {
        val dequeAndRelease = for {
          a <- queueRef.modify(queue => queue.dequeue.swap)
          _ <- fullLock.release
        } yield a

        emptyLock.acquire *> dequeAndRelease
      }

      override def await: F[Unit] = emptyLock.acquire *> emptyLock.release
    }
  }
}

case class TooManyPendingTasksException(maxPendingTasks: Long) extends RuntimeException(s"There are too many tasks in the queue ($maxPendingTasks)")