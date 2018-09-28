# ocicat

ocicat is a purely functional [throttler and rate limiter](https://helpx.adobe.com/coldfusion/api-manager/throttling-and-rate-limiting.html)

## Installing

```scala
libraryDependencies += "com.wix" %% "ocicat" % "0.0.1"
```

## Getting Started

### Throttler

Throttler allows you to throttle requests concurrently with rate you specify.

- decide about throttling rate. For example up to 5 calls in one minute.
```scala
import scala.concurrent.duration._
import com.wix.ocicat.Rate._

val rate = 5 every 1.minute
```

- create a throttler for any container that has cats.effect.Sync instance, e.g. cats.effect.IO.
String is a type of Key we are going throttle on.
```scala
val throttler = Throttler[IO, String](limit every window.millis, Clock.create).unsafeRunSync()
```

- submit a key to the throttle on action you want to be throttled
```scala
val key = UUID.randomUUID().toString
throttler.throttle(key).unsafeRunSync()
throttler.throttle(key).unsafeRunSync()
throttler.throttle(key).unsafeRunSync()
throttler.throttle(key).unsafeRunSync()
throttler.throttle(key).unsafeRunSync()

throttler.throttle(key).unsafeRunSync() // going to throw ThrottleException because of exceeding throttle limits.
```


- in case you are working with future (sic!), use this snippet for the throttler creation
```scala
    def futureThrottler[A](rate: Rate) = {
      new Throttler[Future, A] {
        val throttler0 = Throttler.unsafeCreate[IO, A](rate)
        override def throttle(key: A) = throttler0.throttle(key).unsafeToFuture()
      }
    }
```

### Rate limiter

Rate limiter allows you to execute tasks making sure that specified rate is not exceeded.

- decide about the rate and the queue size. For example up to 5 calls in one minute.
```scala
import scala.concurrent.duration._
import com.wix.ocicat.Rate._

val rate = 5 every 1.minute
val maxPending = Long.MaxValue
```

- create a rate limiter for any container that has cats.effect.Concurrent instance, e.g. cats.effect.IO.
```scala
val rateLimiter = RateLimiter[IO](rate, maxPending, Clock.create).unsafeRunSync()
```

- you can either submit tasks to be executed asynchronously or await task execution
```scala
val task = IO("This is a task for a rate limiter!")

val submitResult: IO[Unit] = rateLimiter.submit(task)
val taskResult: IO[String] = rateLimiter.await(task)
```

- rate limiter will immediately fail task with **TooManyPendingTasksException** if maximum amount of pending tasks is reached

### Circuit breaker

Circuit breaker allows you to prevent failures from constantly occurring during temporary system issues.

- pick **maxFailures** (amount of errors after which circuit breaker will immediately fail tasks) and **resetTimeout** (amount of time after which circuit breaker will allow one task to go through to test the system)
```scala
import scala.concurrent.duration._

val maxFailures = 1
val resetTimeout = 1.minute
```

- create a circuit breaker for any container that has cats.effect.Sync instance, e.g. cats.effect.IO.
```scala
val circuitBreaker = CircuitBreaker[IO](maxFailures, resetTimeout, Clock.create).unsafeRunSync()
```

- circuit breaker will start to protect tasks after **maxFailures** amount of failures
```scala
val failingTask = IO.raiseError(new RuntimeException("Something went terribly wrong"))
val succeedingTask = IO.pure("I'm a happy ???")

circuitBreaker.protect(failingTask).attempt.unsafeRunSync()//Left(RuntimeException("Something went terribly wrong")
circuitBreaker.protect(succeedingTask).attempt.unsafeRunSync()//Left(RejectedException)
Thread.sleep(1000*60)
circuitBreaker.protect(succeedingTask).attempt.unsafeRunSync()//Right("I'm a happy ???")
```

- rate limiter will immediately fail task with **TooManyPendingTasksException** if maximum amount of pending tasks is reached

## Running the tests

```scala
sbt test
```

## Contributing

Please read [CONTRIBUTING.md](https://github.com/wix-incubator/ocicat/blob/master/CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository](https://github.com/wix-incubator/ocicat/tags). 

## Authors

* **Yarosalv Hryniuk** - *Initial work*
* **Valentyn Vakatsiienko** - *Initial work* 

See also the list of [contributors](https://github.com/wix-incubator/ocicat/graphs/contributors) who participated in this project.

## License

This project is licensed under the MIT License - see the [LICENSE.md](https://github.com/wix-incubator/ocicat/blob/master/LICENSE.md) file for details

## Acknowledgments

* Hat tip to anyone whose code was used
* Inspiration
* [upperbound](https://github.com/SystemFw/upperbound)
* etc
