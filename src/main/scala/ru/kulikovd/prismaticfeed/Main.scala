package ru.kulikovd.prismaticfeed

import scala.concurrent.duration.Duration

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

  val feedStorage = system.actorOf(Props(
    new FeedStorage(parser, Duration(config.getMilliseconds("update-interval"), "ms"))
  ))

  IO(Http) ! Http.Bind(
    listener  = system.actorOf(Props(new RssService(feedStorage))),
    interface = config.getString("host"),
    port      = config.getInt("port")
  )
}
