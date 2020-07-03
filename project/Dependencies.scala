import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "2.1.3"

  lazy val doobieCore = "org.tpolecat" %% "doobie-core" % "0.8.8"
  lazy val doobieH2 = "org.tpolecat" %% "doobie-h2" % "0.8.8"
  lazy val doobieTest = "org.tpolecat" %% "doobie-scalatest" % "0.8.8"

  lazy val redis4cats = "dev.profunktor" %% "redis4cats-effects" % "0.10.0"
  lazy val embeddedRedis = "com.github.sebruck" %% "scalatest-embedded-redis" % "0.4.0"
  lazy val specs2core =  "org.specs2" %% "specs2-core" % "4.10.0" % "test"
  lazy val specs2junit = "org.specs2" %% "specs2-junit" % "4.10.0" % "test"
}
