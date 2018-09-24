package com.wix.ocicat

import scala.concurrent.duration.FiniteDuration


case class ThrottlerConfig(window: FiniteDuration, limit: Int) {
  override def toString = {
    s"$limit calls every $window"
  }
}

object ThrottlerConfig {

  implicit class ThrottleConfigLimitOps(limit: Int) {
    def every(window: FiniteDuration) = {
      ThrottlerConfig(window, limit)
    }
  }

}
