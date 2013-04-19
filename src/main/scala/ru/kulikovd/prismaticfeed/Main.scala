package ru.kulikovd.prismaticfeed

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http


object Main extends App {
  
  implicit val system = ActorSystem()

  import system.dispatcher

  system.actorOf(Props[PrismaticParser])

  val handler = system.actorOf(Props[FeedGenerator])

  IO(Http) ! Http.Bind(handler, interface = "localhost", port = 8088)
}
