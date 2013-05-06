import AssemblyKeys._

organization := "ru.kulikovd"

name := "prismatic-feed"

version := "0.0.2-SNAPSHOT"

scalaVersion := "2.10.1"

scalacOptions ++= Seq("-language:postfixOps", "-feature")

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Spray Nightlies" at "http://nightlies.spray.io/"

assemblySettings

jarName in assembly := "prismatic-feed.jar"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.1.2",
  "com.typesafe.akka" %% "akka-slf4j" % "2.1.2",
  "com.typesafe" % "config" % "1.0.0",
  "ch.qos.logback" % "logback-classic" % "1.0.10" % "runtime",
  "io.spray" % "spray-can" % "1.1-2+",
  "io.spray" % "spray-client" % "1.1-2+",
  "io.spray" % "spray-http" % "1.1-2+",
  "io.spray" % "spray-routing" % "1.1-2+",
  "io.spray" %% "spray-json" % "1.2.3"
)

TaskKey[Unit]("upload") := ("scp target/scala-2.10/prismatic-feed.jar " + System.getenv("PRISMATIC_UPLOAD_PATH")).!
