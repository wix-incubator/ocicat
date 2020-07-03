package com.wix.ocicat.storage

import java.util.concurrent.TimeUnit

import cats.effect._
import cats.implicits._
import com.wix.ocicat.Rate
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.algebra.ScriptCommands
import dev.profunktor.redis4cats.connection.{RedisClient, RedisURI}
import dev.profunktor.redis4cats.data.RedisCodec
import dev.profunktor.redis4cats.effect.Log
import dev.profunktor.redis4cats.effects.ScriptOutputType

class RedisStorage[F[_], A](redis: Resource[F, ScriptCommands[F, String, String]],
                            clock: Clock[F])
                           (implicit sync: Sync[F])
  extends ThrottlerStorage[F, A] {

  private def getPrimaryKey(key: A, tick: Long): String = s"${key}_$tick"
  private val script = "local counter = redis.call('INCR', KEYS[1]); " +
    "if counter == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) " +
    "end " +
    "return tostring(counter)"

  override def incrementAndGet(key: A, rate: Rate): F[ThrottlerCapacity[A]] = for {
    now <- clock.realTime(TimeUnit.MILLISECONDS)
    tick = now / rate.window.toMillis
    count <- redis.use(_.eval(script, ScriptOutputType.Value, List(getPrimaryKey(key, tick)), List(rate.window.toSeconds.toString)))
  } yield ThrottlerCapacity(key, count.toInt)

}

object RedisStorage {

  def apply[F[_], A](redisURI: String, clock: Clock[F])
                    (implicit contextShift: ContextShift[F], concurrent: Concurrent[F], log: Log[F]): RedisStorage[F, A] = {

    val commandsApi: Resource[F, ScriptCommands[F, String, String]] =
      for {
        uri    <- Resource.liftF(RedisURI.make[F](redisURI))
        client <- RedisClient[F](uri)
        redis  <- Redis[F].fromClient(client, RedisCodec.Utf8)
      } yield redis

    new RedisStorage[F, A](commandsApi, clock: Clock[F])
  }
}
