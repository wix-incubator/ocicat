package com.wix.ocicat

import java.util.concurrent.TimeUnit

import cats.effect.{Clock, IO}

class FakeClock(var time: Long = System.currentTimeMillis()) extends Clock[IO] {

  override def realTime(unit: TimeUnit) = IO {
    unit.convert(time, TimeUnit.MILLISECONDS)
  }

  override def monotonic(unit: TimeUnit) = IO {
    unit.convert(time, TimeUnit.MILLISECONDS)
  }

  def add(delta: Int): Unit = {
    time = time + delta.toLong
  }
}