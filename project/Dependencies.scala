import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "1.0.0"

  lazy val doobieCore = "org.tpolecat" %% "doobie-core" % "0.8.8"
  lazy val doobieH2 = "org.tpolecat" %% "doobie-h2" % "0.8.8"
  lazy val doobieTest = "org.tpolecat" %% "doobie-scalatest" % "0.8.8"
}
