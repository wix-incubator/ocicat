# ocicat

ocicat is a purely functional [throttler and rate limiter(coming)](https://helpx.adobe.com/coldfusion/api-manager/throttling-and-rate-limiting.html). It allows you to throttle requests concurrently with rate you specify.

## Installing

```scala
libraryDependencies += "com.wix" %% "ocicat" % "0.0.1"
```

## Getting Started

- decide about throttling rate. For example up to 5 calls in one minute.
```scala
import scala.concurrent.duration._
import com.wix.ocicat.ThrottlerConfig._

val config = 5 every 1.minute
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


- in case you are working with future, use this snippet for the throttler creation
```scala
    def futureThrottler[A](config: ThrottlerConfig) = {
      new Throttler[Future, A] {
        val throttler0 = Throttler.unsafeCreate[IO, A](config)
        override def throttle(key: A) = throttler0.throttle(key).unsafeToFuture()
      }
    }
```

## Running the tests

```scala
sbt test
```


## Contributing

Please read [CONTRIBUTING.md](https://gist.github.com/PurpleBooth/b24679402957c63ec426) for details on our code of conduct, and the process for submitting pull requests to us.

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository](https://github.com/your/project/tags). 

## Authors

* **Yarosalv Hryniuk** - *Initial work*
* **Valentyn Vakatsiienko** - *Initial work* 

See also the list of [contributors](https://github.com/your/project/contributors) who participated in this project.

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details

## Acknowledgments

* Hat tip to anyone whose code was used
* Inspiration
* [upperbound](https://github.com/SystemFw/upperbound)
* etc
