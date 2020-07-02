package com.wix.ocicat.storage

import java.util.concurrent.ConcurrentHashMap

import cats.effect.Sync
import cats.implicits._

// Ref[F, Map[]]
class InMemoryStorage[F[_], A](memory: ConcurrentHashMap[String, Int])(implicit F: Sync[F]) extends ThrottlerStorage[F, A] {

  private def getPrimaryKey(key: A, tick: Long): String = s"${key}_$tick"

  override def incrementAndGet(key: A, tick: Long, expirationMillis: Long): F[ThrottlerCapacity[A]] = for {
    value <- Sync[F].pure(memory.get(getPrimaryKey(key, tick)))
    newValue = value + 1
    _ <- Sync[F].pure(memory.put(getPrimaryKey(key, tick), newValue))
  } yield ThrottlerCapacity(key, newValue)
}

object InMemoryStorage {
  def apply[F[_], A]()(implicit F: Sync[F]): InMemoryStorage[F, A] = {
    new InMemoryStorage[F, A](new ConcurrentHashMap())
  }
}