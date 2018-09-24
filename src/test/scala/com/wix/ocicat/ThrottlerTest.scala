package com.wix.ocicat

import java.util.concurrent.TimeUnit

import cats.effect
import cats.effect.{Clock, IO}
import org.scalatest._

import scala.concurrent.duration.{FiniteDuration, TimeUnit}

class ThrottlerTest extends FlatSpec with Matchers {

  "Throttler" should "throttle" in {
    val fakeTimer = new FakeTimer()
    implicit val cs = fakeTimer.timer
    val throttler = Throttler[IO, Int](1000, 3).unsafeRunSync()

    throttler.throttle(1).unsafeRunSync()
    throttler.throttle(1).unsafeRunSync()
    throttler.throttle(1).unsafeRunSync()

    fakeTimer.time = fakeTimer.time + 2000
    throttler.throttle(1).unsafeRunSync()
    throttler.throttle(1).unsafeRunSync()
    throttler.throttle(1).unsafeRunSync()


  }

  it should "sad" in {
    val fakeTimer = new FakeTimer()
    implicit val cs = fakeTimer.timer
    fakeTimer.time = (fakeTimer.time / 1000) * 1000 + 999
    val throttler = Throttler[IO, Int](1000, 3).unsafeRunSync()

    throttler.throttle(1).unsafeRunSync()
    throttler.throttle(1).unsafeRunSync()
    throttler.throttle(1).unsafeRunSync()

    fakeTimer.time = fakeTimer.time + 500
    throttler.throttle(1).unsafeRunSync()

  }
}

class FakeTimer(var time: Long = System.currentTimeMillis()) {

  def timer: effect.Timer[IO] = new effect.Timer[IO] {
    override def clock = new Clock[IO] {
      override def realTime(unit: TimeUnit) = IO {
        unit.convert(time, TimeUnit.MILLISECONDS)
      }

      override def monotonic(unit: TimeUnit) = ???
    }

    override def sleep(duration: FiniteDuration) = ???
  }
}
