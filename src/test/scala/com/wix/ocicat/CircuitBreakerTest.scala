package com.wix.ocicat

import java.util.concurrent.TimeUnit

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.implicits._
import org.scalatest.{FlatSpec, Matchers}
import scala.concurrent.duration._
import cats.implicits._
import com.wix.ocicat.CircuitBreaker.RejectedException

class CircuitBreakerTest extends FlatSpec with Matchers {

  trait ctx {
    val errorTask = IO.raiseError(new RuntimeException("some exception"))

    val fakeClock = new FakeClock()

    val maxFailure = 10
    val resetTimeoutMillis = 10 * 1000
    val circuitBreaker = CircuitBreaker[IO](maxFailure, FiniteDuration(resetTimeoutMillis.toLong, TimeUnit.MILLISECONDS), fakeClock).unsafeRunSync()

    val notRejected: Either[Throwable, _] => Boolean = {
      case Left(_: RejectedException) => false
      case _ => true
    }
  }

  "CircuitBreaker" should "be open if all tasks are successful" in new ctx {
    val counter = Ref.unsafe[IO, Int](0)

    circuitBreaker.protect(counter.update(_ + 1)).replicateA(100).unsafeRunSync()

    counter.get.unsafeRunSync() should be(100)
  }

  it should "reject after 10 errors" in new ctx {


    circuitBreaker.protect(errorTask).attempt.replicateA(10).unsafeRunSync()

    assertThrows[RejectedException] {
      circuitBreaker.protect(IO.unit).unsafeRunSync()
    }
  }

  it should "reset failures count in case of successful task" in new ctx {

    circuitBreaker.protect(errorTask).attempt.replicateA(9).unsafeRunSync()

    circuitBreaker.protect(IO(1)).unsafeRunSync() should be(1)

    circuitBreaker.protect(errorTask).attempt.unsafeRunSync()

    circuitBreaker.protect(IO(1)).unsafeRunSync() should be(1)
  }

  it should "run probe after denying expired" in new ctx {
    circuitBreaker.protect(errorTask).attempt.replicateA(maxFailure).unsafeRunSync()

    assertThrows[RejectedException] {
      circuitBreaker.protect(IO.unit).unsafeRunSync()
    }

    fakeClock.add(resetTimeoutMillis + 10)

    circuitBreaker.protect(IO(10)).unsafeRunSync() should be(10)

    circuitBreaker.protect(IO(11)).unsafeRunSync() should be(11)

    circuitBreaker.protect(errorTask).attempt.replicateA(maxFailure - 1).unsafeRunSync()

    circuitBreaker.protect(IO(12)).unsafeRunSync() should be(12)

    circuitBreaker.protect(errorTask).attempt.replicateA(maxFailure).map(_.forall(notRejected)).unsafeRunSync() should be(true)

    assertThrows[RejectedException] {
      circuitBreaker.protect(IO.unit).unsafeRunSync()
    }
  }

  it should "" in new ctx {
    circuitBreaker.protect(errorTask).attempt.replicateA(maxFailure).unsafeRunSync()

  }

}
