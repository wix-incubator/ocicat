package com.wix.ocicat

import scala.concurrent.duration.FiniteDuration


case class ThrottleConfig(window: FiniteDuration, limit: Int)

object ThrottleConfig {

  implicit class ThrottleConfigLimitOps(limit: Int) {
    def every(window: FiniteDuration) = {
      ThrottleConfig(window, limit)
    }
  }

}
