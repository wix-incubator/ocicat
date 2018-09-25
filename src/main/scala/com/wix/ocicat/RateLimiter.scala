package com.wix.ocicat

import cats.{MonadError, effect}
import cats.effect.Concurrent
import cats.effect.concurrent.{Ref, Semaphore}
import cats.implicits._

import scala.collection.immutable

class RateLimiter {

}

trait Queue[F[_], A] {
  def enqueue(a: A): F[Unit]

  def dequeue: F[A]
}

object Queue {
  def apply[F[_]: Concurrent, A](bound: Long): F[Queue[F, A]] = {
    for {
      upperBound <- Semaphore(0)
      lowerBound <- Semaphore(bound)
      queueRef <- Ref.of(new immutable.Queue[A]())
    } yield new Queue[F, A] {

      override def enqueue(a: A): F[Unit] = for {
        _ <- lowerBound.tryAcquire ifM (
          ifTrue = upperBound.release *> queueRef.update(queue => queue.enqueue(a)),
          ifFalse = Concurrent[F].raiseError[Unit](LimitReachedException()))
      } yield {}

      override def dequeue: F[A] = {
        val dequeAndRelease = for {
          a <- queueRef.modify(queue => queue.dequeue.swap)
          _ <- lowerBound.release
        } yield a

        upperBound.acquire *> dequeAndRelease
      }
    }
  }
}

case class LimitReachedException() extends RuntimeException("")