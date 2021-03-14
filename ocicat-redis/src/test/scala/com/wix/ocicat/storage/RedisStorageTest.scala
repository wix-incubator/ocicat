package com.wix.ocicat.storage

import cats.effect.{ContextShift, IO}
import com.wix.ocicat.{FakeClock, Rate}
import dev.profunktor.redis4cats.effect.Log.Stdout._
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import cats.implicits._
import com.github.sebruck.EmbeddedRedis
import com.wix.ocicat.Utils._
import redis.embedded.RedisServer

import scala.concurrent.ExecutionContextExecutor

class RedisStorageTest extends FlatSpec with Matchers with EmbeddedRedis with BeforeAndAfterAll {

  var redis: RedisServer = _

  override def beforeAll(): Unit = {
    redis = startRedis()
  }

  override def afterAll(): Unit = {
    stopRedis(redis)
  }

  "RedisStorage" should "increment capacity by 1" in new ctx {
    storage.incrementAndGet(key1, rate).unsafeRunSync()
    val result: ThrottlerCapacity[String] = storage.incrementAndGet(key1, rate).unsafeRunSync()

    result.counts shouldBe 2
  }

  "RedisStorage" should "increment capacity by 2 from two threads on the same tick" in new ctx {
    val thr1: IO[ThrottlerCapacity[String]] = storage.incrementAndGet(key1, rate)
    val thr2: IO[ThrottlerCapacity[String]] = storage.incrementAndGet(key1, rate)

    IO.racePair(thr1, thr2)(cs).unsafeRunSync()

    storage.incrementAndGet(key1, rate).unsafeRunSync().counts shouldBe 3
  }

  "RedisStorage" should "remove expired data" in new ctx {
    storage.incrementAndGet(key1, rate).replicateA(2).unsafeRunSync()

    moveToNextTimeWindow(rate)

    val result: ThrottlerCapacity[String] =
      storage.incrementAndGet(key1, rate).unsafeRunSync()

    result.counts shouldBe 1
  }


  trait ctx {

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
