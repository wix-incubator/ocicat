package com.wix.ocicat


import cats.effect.IO
import cats.implicits._
import com.wix.ocicat.Rate._
import org.scalatest._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class ThrottlerTest extends FlatSpec with Matchers {

  trait ctx {
    def window: Int

    def limit: Int


    implicit val cs = IO.contextShift(global)
    val fakeClock = new FakeClock()
    val throttler = Throttler[IO, Int](limit every window.millis, fakeClock).unsafeRunSync()
  }

  "Throttler" should "not throttle" in new ctx {
    override def window = 1000

    override def limit = 3

    throttler.throttle(1).replicateA(limit).unsafeRunSync()

    fakeClock.add(window)

    throttler.throttle(1).replicateA(limit).unsafeRunSync()

  }

  it should "not throttle in the next tick" in new ctx {
    override def window = 1000

    override def limit = 3

    fakeClock.time = (fakeClock.time / 1000) * 1000 + 999

    throttler.throttle(1).replicateA(limit).unsafeRunSync()

    fakeClock.time = fakeClock.time + 500
    throttler.throttle(1).unsafeRunSync()

  }
  it should "throttle in case limit of calls is exceeded" in new ctx {
    override def window = 1000

    override def limit = 3


    throttler.throttle(1).replicateA(3).unsafeRunSync()
    assertThrows[ThrottleException] {
      throttler.throttle(1).unsafeRunSync()
    }
  }

  it should "throttle after changing tick" in new ctx {
    override def window = 1000

    override def limit = 3

    throttler.throttle(1).replicateA(limit).unsafeRunSync()
    fakeClock.add(window)
    throttler.throttle(1).replicateA(limit).unsafeRunSync()
    assertThrows[ThrottleException] {
      throttler.throttle(1).unsafeRunSync()
    }
    fakeClock.add(window)
    throttler.throttle(1).unsafeRunSync()

  }

  it should "fail on throttle construction" in {
    val fakeClock = new FakeClock()

    implicit val cs = IO.contextShift(global)

    assertThrows[InvalidRateException] {
      Throttler[IO, Int](-1 every -1.millis, fakeClock).unsafeRunSync()
    }
  }

  "Throttler with scala.concurrent.Future" should "throttle" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.duration._
    implicit val cs = IO.contextShift(global)
    def futureThrottler[A](config: Rate) = {
      new Throttler[Future, A] {
        val throttler0 = Throttler.unsafeCreate[IO, A](config)
        override def throttle(key: A) = throttler0.throttle(key).unsafeToFuture()
        override def await(key: A) = ???
      }
    }

    val key = "key1"
    val throttler = futureThrottler[String](5 every 100.seconds)

    Await.result(Future.sequence(Seq.fill(5)(throttler.throttle(key))), 1 minute)

    assertThrows[ThrottleException] {
      Await.result(throttler.throttle(key), 1 minute)
    }

  }
}
