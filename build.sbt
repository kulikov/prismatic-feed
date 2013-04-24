import AssemblyKeys._

organization := "ru.kulikovd"

name := "prismatic-feed"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.10.1"

scalacOptions ++= Seq("-language:postfixOps", "-feature")

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

assemblySettings

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.1.2",
  "com.typesafe.akka" %% "akka-slf4j" % "2.1.2",
  "com.typesafe" % "config" % "1.0.0",
  "ch.qos.logback" % "logback-classic" % "1.0.10" % "runtime",
  "io.spray" % "spray-can" % "1.1-M8-SNAPSHOT",
  "io.spray" % "spray-client" % "1.1-M8-SNAPSHOT",
  "io.spray" % "spray-http" % "1.1-M8-SNAPSHOT",
  "io.spray" % "spray-routing" % "1.1-M8-SNAPSHOT",
  "io.spray" %% "spray-json" % "1.2.3"
)
