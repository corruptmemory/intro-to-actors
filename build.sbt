name := "intro-to-actors"

organization := "com.jsuereth"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

scalaVersion := "2.10.2"

scalacOptions += "-feature"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.2.0-RC2",
  "org.typesafe.async" %% "scala-async" % "1.0.0-SNAPSHOT",
  "org.specs2" %% "specs2" % "2.0" % "test"
)
