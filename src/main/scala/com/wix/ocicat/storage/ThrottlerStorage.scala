package com.wix.ocicat.storage

import com.wix.ocicat.Rate

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
   * Each invocation must be linked (added) within a certain rate window and has to be expired at the end of this window.
   * For example:
   * Having a throttler with a key `A` invoked for a window `W1` storage has to store something like:
   * ```
   *  key:A; window:W1; expired:CurrentTime + window; amount: 1
   * ```
   * Next time a throttler will be invoked with the same key and within the same window we have to have next state:
   * ```
   *  key:A; window:W1; expired:CurrentTime + window; amount: 2
   * ```
   * In case invocation of the key `A` is done during next window (for example `W2`) we have to have this state:
   * ```
   *  key:A; window:W2; expired:CurrentTime + window; amount: 1
   * ```
   *
   * @param key throttler key
   * @param rate rate configuration of a throttler
   * @return capacity of a throttler key of a current tick
   */
  def incrementAndGet(key: A, rate: Rate): F[ThrottlerCapacity[A]]
}
