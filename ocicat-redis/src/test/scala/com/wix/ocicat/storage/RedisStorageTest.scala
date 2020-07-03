package com.wix.ocicat.storage

import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.github.sebruck.EmbeddedRedis
import com.wix.ocicat.{FakeClock, Rate}
import com.wix.ocicat.Utils._
import dev.profunktor.redis4cats.effect.Log.Stdout._
import org.specs2.mutable.SpecWithJUnit
import org.specs2.specification.{BeforeAfterAll, Scope}
import redis.embedded.RedisServer

import scala.concurrent.ExecutionContextExecutor

class RedisStorageTest extends SpecWithJUnit with EmbeddedRedis with BeforeAfterAll {

  var redis: RedisServer = _

  override def beforeAll(): Unit = {
    redis = startRedis()
  }

  override def afterAll(): Unit = {
    stopRedis(redis)
  }

  "RedisStorage" should {
    "increment capacity by 1" in new ctx {
      storage.incrementAndGet(key1, rate).unsafeRunSync()
      val result: ThrottlerCapacity[String] = storage.incrementAndGet(key1, rate).unsafeRunSync()

      result.counts must beEqualTo(2)
    }

    "increment capacity by 2 from two threads on the same tick" in new ctx {
      val thr1: IO[ThrottlerCapacity[String]] = storage.incrementAndGet(key1, rate)
      val thr2: IO[ThrottlerCapacity[String]] = storage.incrementAndGet(key1, rate)

      IO.racePair(thr1, thr2)(cs).unsafeRunSync()

      storage.incrementAndGet(key1, rate).unsafeRunSync().counts must beEqualTo(3)
    }

    "remove expired data" in new ctx {
      storage.incrementAndGet(key1, rate).replicateA(2).unsafeRunSync()

      moveToNextTimeWindow(rate)

      val result: ThrottlerCapacity[String] =
        storage.incrementAndGet(key1, rate).unsafeRunSync()

      result.counts must beEqualTo(1)
    }
  }

  trait ctx extends Scope {

    import com.wix.ocicat.Rate._

    import scala.concurrent.duration._

    implicit val ec: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
    implicit val cs: ContextShift[IO] = IO.contextShift(ec)

    private val clock = new FakeClock()
    val storage: RedisStorage[IO, String] = RedisStorage[IO, String](s"redis://localhost:${redis.ports().get(0)}", clock)

    val rate: Rate = 3 every 1.minutes
    val key1: String = randomString(5)

    def moveToNextTimeWindow(rate: Rate): Unit = clock.add((rate.window.toMillis + 1).toInt)
  }

}
