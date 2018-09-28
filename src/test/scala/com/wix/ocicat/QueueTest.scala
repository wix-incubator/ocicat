package com.wix.ocicat

import cats.effect.IO
import cats.effect.internals.IOContextShift
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.language.postfixOps
import cats.implicits._
class QueueTest extends FlatSpec with Matchers {

  trait ctx {
    implicit val ec = scala.concurrent.ExecutionContext.global
    implicit val cs = IOContextShift.global
    implicit val timer = IO.timer(ec)
  }

  "Queue" should "be able to enqueue and dequeue elements" in new ctx {
    val res = (for {
      q <- Queue[IO, Int](2)
      _ <- q.enqueue(1)
      _ <- q.enqueue(2)
      one <- q.dequeue
      two <- q.dequeue
    } yield one :: two :: Nil).unsafeRunSync()

    res shouldEqual 1 :: 2 :: Nil
  }

  it should "not allow to exceed its capacity" in new ctx {
    val res = (for {
      q <- Queue[IO, Int](1)
      _ <- q.enqueue(1)
      _ <- q.enqueue(2)
    } yield {}).attempt.unsafeRunSync()

    res shouldEqual Left(TooManyPendingTasksException(1))
  }

  it should "block on attempt to dequeue from an empty queue" in new ctx {
    val q = Queue[IO, Int](1).unsafeRunSync()

    val enqueue = for {
      _ <- IO.sleep(100 millis)
      _ <- q.enqueue(1)
    } yield {}

    val elem = (enqueue, q.dequeue).mapN { case (_, e) => e}.unsafeRunSync()
    elem shouldEqual 1
  }

}
