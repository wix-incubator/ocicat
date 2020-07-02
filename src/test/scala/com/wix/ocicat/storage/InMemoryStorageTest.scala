package com.wix.ocicat.storage

import cats.effect.IO
import com.wix.ocicat.FakeClock
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext

class InMemoryStorageTest extends FlatSpec with Matchers {

  "InMemoryStorage" should "increment capacity by 1" in new ctx {
    storage.incrementAndGet(key1, tick, futureMillis).unsafeRunSync()
    private val result: ThrottlerCapacity[String] = storage.incrementAndGet(key1, tick, futureMillis).unsafeRunSync()

    result.counts shouldBe 2
  }

  "InMemoryStorage" should "increment capacity by 2 from two threads on the same tick" in new ctx {
    private val thr1: IO[ThrottlerCapacity[String]] = storage.incrementAndGet(key1, tick, futureMillis)
    private val thr2: IO[ThrottlerCapacity[String]] = storage.incrementAndGet(key1, tick, futureMillis)

    IO.race(thr1, thr2)(cs).unsafeRunSync()

    storage.incrementAndGet(key1, tick, futureMillis).unsafeRunSync().counts shouldBe 3
  }

/*  "InMemoryStorage" should "remove expired data" in new ctx {
    storage.incrementAndGet(key1, tick, futureMillis).unsafeRunSync()
    private val result: ThrottlerCapacity[String] = storage.incrementAndGet(key1, tick, expiredMillis).unsafeRunSync()

    result.counts shouldBe 0
  }*/

  trait ctx {
    private val clock = new FakeClock()
    private val oneHourMillis = 60*60*1000
    val cs = IO.contextShift(ExecutionContext.global)
    val storage = InMemoryStorage[IO, String]()

    val key1 = "key1"
    val tick = 1L
    val futureMillis = clock.time + oneHourMillis
    val expiredMillis = clock.time - oneHourMillis
  }

}
