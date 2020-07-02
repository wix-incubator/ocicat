package com.wix.ocicat.storage

import cats.effect.IO
import com.wix.ocicat.{FakeClock, Rate}
import org.scalatest.{FlatSpec, Matchers}
import cats.implicits._

import scala.concurrent.ExecutionContext

class InMemoryStorageTest extends FlatSpec with Matchers {

  "InMemoryStorage" should "increment capacity by 1" in new ctx {
    storage.incrementAndGet(key1, rate).unsafeRunSync()
    private val result: ThrottlerCapacity[String] = storage.incrementAndGet(key1, rate).unsafeRunSync()

    result.counts shouldBe 2
  }

  "InMemoryStorage" should "increment capacity by 2 from two threads on the same tick" in new ctx {
    private val thr1: IO[ThrottlerCapacity[String]] = storage.incrementAndGet(key1, rate)
    private val thr2: IO[ThrottlerCapacity[String]] = storage.incrementAndGet(key1, rate)

    IO.racePair(thr1, thr2)(cs).unsafeRunSync()

    storage.incrementAndGet(key1, rate).unsafeRunSync().counts shouldBe 3
  }

  "InMemoryStorage" should "remove expired data" in new ctx {
    storage.incrementAndGet(key1, rate).replicateA(2).unsafeRunSync()

    moveToNextTimeWindow(rate)

    private val result: ThrottlerCapacity[String] = storage.incrementAndGet(key1, rate).unsafeRunSync()

    result.counts shouldBe 1
  }

  trait ctx {
    import com.wix.ocicat.Rate._
    import scala.concurrent.duration._

    private val clock = new FakeClock()

    val cs = IO.contextShift(ExecutionContext.global)
    val storage = InMemoryStorage[IO, String](clock)

    val rate: Rate = 3 every 10.minutes

    val key1 = "key1"

    def moveToNextTimeWindow(rate: Rate) = clock.add(((rate.window.toMillis + 1)).toInt)
  }

}
