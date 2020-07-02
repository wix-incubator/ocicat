package com.wix.ocicat.storage

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._

class InMemoryStorage[F[_], A](memory: Ref[F, Map[String, Int]])(implicit F: Sync[F]) extends ThrottlerStorage[F, A] {

  private def getPrimaryKey(key: A, tick: Long): String = s"${key}_$tick"

  override def incrementAndGet(key: A, tick: Long, expirationMillis: Long): F[ThrottlerCapacity[A]] = for {
    newValue <- memory.modify(map => {
      val newVal = map.get(getPrimaryKey(key, tick)).map(count => count + 1).getOrElse(1)
      (Map(getPrimaryKey(key, tick) -> newVal), newVal)
    })
  } yield ThrottlerCapacity(key, newValue)
}

object InMemoryStorage {
  def apply[F[_], A]()(implicit F: Sync[F]): InMemoryStorage[F, A] = {
    new InMemoryStorage[F, A](Ref.unsafe(Map[String, Int]()))
  }
}