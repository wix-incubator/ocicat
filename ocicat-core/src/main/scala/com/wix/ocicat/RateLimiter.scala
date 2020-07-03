package com.wix.ocicat

import cats.effect._
import cats.effect.concurrent.{Deferred, Ref, Semaphore}
import cats.implicits._
import cats.effect.implicits._
import com.wix.ocicat.storage.InMemoryStorage

import scala.collection.immutable

trait RateLimiter[F[_]] {
  def submit[A](f: F[A]): F[Unit]
  def await[A](f: F[A]): F[A]
  def stop: F[Unit]
}

object RateLimiter {
  def unsafeCreate[F[_] : Effect : Concurrent](rate: Rate, maxPending: Long, clock: Clock[F], cs: ContextShift[F]): RateLimiter[F] =
    Effect[F].toIO(apply(rate, maxPending, clock, cs)).unsafeRunSync()

  def apply[F[_] : Concurrent](rate: Rate, maxPending: Long, clock: Clock[F], cs: ContextShift[F]): F[RateLimiter[F]] = for {
    q <- Queue[F, F[_]](maxPending)
    throttler <- Throttler[F, Int](rate, InMemoryStorage[F, Int](clock))
    state <- Ref.of(true)
    _ <- runLoop[F](q, throttler, state, cs).start
  } yield new RateLimiter[F] {

    override def submit[A](f: F[A]): F[Unit] = q.enqueue(f)

    override def await[A](f: F[A]): F[A] = for {
      p <- Deferred[F, Either[Throwable, A]]
      a <- q.enqueue(f.attempt.flatTap(p.complete)) *> p.get.rethrow
    } yield a

    override def stop: F[Unit] = state.update(_ => false)
  }

  private def runLoop[F[_]: Concurrent](q: Queue[F, F[_]], throttler: Throttler[F, Int], state: Ref[F, Boolean], cs: ContextShift[F]) = (for {
    _ <- q.await
    continue <- state.get
    _ <- throttler.throttle(0).adaptError { case _ => TaskThrottled(continue) }
    a <- q.dequeue
    _ <- cs.shift
    _ <- a.start.void
  } yield continue).attempt.iterateWhile {
    case Left(TaskThrottled(continue)) => continue
    case Right(continue) => continue
    case _ => false
  }

  private case class TaskThrottled(continue: Boolean) extends RuntimeException(s"Task throttled, should continue: $continue")
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