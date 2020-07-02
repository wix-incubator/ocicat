package com.wix.ocicat.storage

/**
 * Trait that defines a contract for throttler storage.
 * This interface can be used to define distributed storage too.
 *
 * @tparam F context of a storage
 * @tparam A throttler key type
 */
trait ThrottlerStorage[F[_], A] {
  /**
   * Increments an amount of throttler invocations by `1`.
   * Each invocation is linked with a tick and has an expiration time for different distributed storage.
   *
   * @param key throttler key
   * @param tick number of a time interval that invocation occurred
   * @param expirationMillis time when tick amounts will be expired
   * @return Capacity of a throttler key of a current tick
   */
  def incrementAndGet(key: A, tick: Long, expirationMillis: Long): F[ThrottlerCapacity[A]]
}
