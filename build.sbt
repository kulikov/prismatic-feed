import AssemblyKeys._

organization := "ru.kulikovd"

name := "prismatic-feed"

version := "0.0.3-SNAPSHOT"

scalaVersion := "2.10.3"

scalacOptions ++= Seq(
  "-language:postfixOps",
  "-language:implicitConversions",
  "-feature",
  "-deprecation",
  "-Yinline-warnings",
  "-optimise",
  "-encoding", "utf8"
)

javacOptions ++= Seq("-source", "1.7")

javaOptions in run += "-server -XX:+TieredCompilation -XX:+AggressiveOpts"

resolvers ++= Seq(
  "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
  "Spray" at "http://repo.spray.io/"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.0",
  "com.typesafe.akka" %% "akka-slf4j" % "2.3.0",
  "com.typesafe" % "config" % "1.2.0",
  "ch.qos.logback" % "logback-classic" % "1.0.10" % "runtime",
  "io.spray" % "spray-can" % "1.3.0",
  "io.spray" % "spray-client" % "1.3.0",
  "io.spray" % "spray-http" % "1.3.0",
  "io.spray" % "spray-routing" % "1.3.0",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.3.2",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.3.2"
)

TaskKey[Unit]("upload") := ("scp target/scala-2.10/prismatic-feed.jar " + System.getenv("PRISMATIC_UPLOAD_PATH")).!

assemblySettings

jarName in assembly := "prismatic-feed.jar"
