package com.wix.ocicat

import java.util.concurrent.TimeoutException

import cats.effect.{ContextShift, IO}
import cats.effect.concurrent.Deferred
import com.wix.ocicat.Rate._
import org.scalatest.{EitherValues, FlatSpec, Matchers}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.language.postfixOps

class RateLimiterTest extends FlatSpec with Matchers with EitherValues {

  trait ctx {
    implicit val ec: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)

    val fakeClock = new FakeClock()
    def makeLimiter(rate: Rate, maxPending: Long): IO[RateLimiter[IO]] = RateLimiter[IO](rate, maxPending, fakeClock, cs)
  }

  "RateLimiter" should "run all jobs in the current tick" in new ctx {
    val (one, two) = (for {
      limiter <- makeLimiter(100 every 1000.millis, 2)
      one <- limiter.await(IO(1))
      two <- limiter.await(IO(2))
      _ <- limiter.stop
    } yield (one, two)).unsafeRunSync()

    one shouldEqual 1
    two shouldEqual 2
  }

  it should "move throttled jobs to the next tick" in new ctx {
    fakeClock.time = (fakeClock.time / 100) * 100 + 99

    val (res1, res2Throttled, res2) = (for {
      limiter <- makeLimiter(1 every 10.millis, 2)

      p1 <- Deferred[IO, Int]
      p2 <- Deferred[IO, Int]

      _ <- limiter.submit(p1.complete(1))
      _ <- limiter.submit(p2.complete(2))

      res1 <- p1.get
      res2Throttled <- p2.get.timeout(20 millis)(IO.timer(ec), cs).attempt
      _ = fakeClock.add(2)
      res2 <- p2.get

      _ <- limiter.stop
    } yield (res1, res2Throttled, res2)).unsafeRunSync()

    res1 shouldEqual 1
    res2Throttled.left.value shouldBe a[TimeoutException]
    res2 shouldEqual 2
  }

  it should "drop jobs if the queue is full" in new ctx {
    val (one, two, three) = (for {
      limiter <- makeLimiter(1 every 1000.millis, 1)
      one <- limiter.await(IO(1))
      two <- limiter.submit(IO(2)).attempt
      three <- limiter.submit(IO(3)).attempt
      _ <- limiter.stop
    } yield (one, two, three)).unsafeRunSync()

    one shouldEqual 1
    two should be ('right)
    three.left.value shouldBe a[TooManyPendingTasksException]
  }
}
