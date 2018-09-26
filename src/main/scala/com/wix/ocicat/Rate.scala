package com.wix.ocicat

import scala.concurrent.duration.FiniteDuration


case class Rate(window: FiniteDuration, limit: Int) {
  override def toString = {
    s"$limit calls every $window"
  }
}

object Rate {

  implicit class RateLimitOps(limit: Int) {
    def every(window: FiniteDuration) = {
      Rate(window, limit)
    }
  }

}
