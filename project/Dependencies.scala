import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "2.3.3"

  lazy val redis4cats = "dev.profunktor" %% "redis4cats-effects" % "0.12.0"
  lazy val embeddedRedis = "com.github.sebruck" %% "scalatest-embedded-redis" % "0.4.0"
}
