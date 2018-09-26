package com.wix.ocicat

import java.util.concurrent.TimeoutException

import cats.effect.{Clock, IO, Timer}
import cats.effect.concurrent.Deferred
import cats.effect.internals.IOContextShift
import com.wix.ocicat.Rate._
import org.scalatest.{EitherValues, FlatSpec, Matchers}
import cats.implicits._

import scala.concurrent.duration._
import scala.language.postfixOps

class RateLimiterTest extends FlatSpec with Matchers with EitherValues {

  trait ctx {
    implicit val ec = scala.concurrent.ExecutionContext.global
    implicit val cs = IOContextShift.global

    val fakeClock = new FakeClock()
    implicit val fakeTimer = new Timer[IO] {
      override def clock: Clock[IO] = fakeClock

      override def sleep(duration: FiniteDuration): IO[Unit] = IO.timer(ec).sleep(duration)
    }

    def makeLimiter(rate: Rate, maxPending: Long) = RateLimiter[IO](rate, fakeTimer, maxPending)
  }

  "RateLimiter" should "run all jobs in the current tick" in new ctx {
    val (one, two) = (for {
      limiter <- makeLimiter(100 every 1000.millis, 2)
      one <- limiter.await(IO(1))
      two <- limiter.await(IO(2))
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
      _ = fakeClock.add(20)
    } yield (res1, res2Throttled, res2)).unsafeRunSync()

    res1 shouldEqual 1
    res2Throttled.left.value shouldBe a[TimeoutException]
    res2 shouldEqual 2
  }
}
