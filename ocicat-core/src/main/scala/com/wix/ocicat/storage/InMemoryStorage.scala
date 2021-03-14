package com.wix.ocicat.storage

import java.util.concurrent.TimeUnit

import cats.effect.{Clock, Sync}
import cats.effect.concurrent.Ref
import cats.implicits._
import com.wix.ocicat.Rate

class InMemoryStorage[F[_], A](memory: Ref[F, Map[String, InMemoryValue]], clock: Clock[F])(implicit F: Sync[F]) extends ThrottlerStorage[F, A] {

  private def getPrimaryKey(key: A, tick: Long): String = s"${key}_$tick"

  override def incrementAndGet(key: A, rate: Rate): F[ThrottlerCapacity[A]] = for {
    now <- clock.realTime(TimeUnit.MILLISECONDS)
    tick = now / rate.window.toMillis
    newValue <- memory.modify(map => {
      val newVal = map
        .filter(e => e._2.expire >= now)
        .get(getPrimaryKey(key, tick))
        .map(value => value.copy(counts = value.counts + 1)).getOrElse(InMemoryValue(1, now + rate.window.toMillis))
      (Map(getPrimaryKey(key, tick) -> newVal), newVal)
    })
  } yield ThrottlerCapacity(key, newValue.counts)
}

object InMemoryStorage {
  def apply[F[_], A](clock: Clock[F])(implicit F: Sync[F]): InMemoryStorage[F, A] = {
    new InMemoryStorage[F, A](Ref.unsafe(Map[String, InMemoryValue]()), clock)
  }
}

private[this] case class InMemoryValue(counts: Int, expire: Long)
