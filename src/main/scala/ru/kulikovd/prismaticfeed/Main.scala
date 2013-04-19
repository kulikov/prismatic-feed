package ru.kulikovd.prismaticfeed

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import com.typesafe.config.ConfigFactory
import spray.can.Http


object Main extends App {

  implicit val system = ActorSystem()

  val config = ConfigFactory.load().getConfig("prismaticfeed")

  val parser = system.actorOf(Props(new PrismaticParser(
    config.getString("username"),
    config.getString("password")
  )))

  val feedStorage = system.actorOf(Props(new FeedStorage(parser)))

  feedStorage ! UpdateFeed


  IO(Http) ! Http.Bind(system.actorOf(Props(new FeedGenerator(feedStorage))), interface = "localhost", port = config.getInt("port"))
}
