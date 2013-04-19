package ru.kulikovd.prismaticfeed

import scala.concurrent.duration._
import akka.pattern.ask
import akka.util.Timeout
import akka.actor._
import spray.can.Http
import spray.can.server.Stats
import spray.util._
import spray.http._
import HttpMethods._
import MediaTypes._


class FeedGenerator extends Actor with ActorLogging {
  import context.dispatcher

  implicit val timeout = Timeout(1 second)

  def receive = {
    case _: Http.Connected => sender ! Http.Register(self)

    case HttpRequest(GET, Uri.Path("/ping"), _, _, _) =>
      log.info("test-test")
      sender ! HttpResponse(entity = "PONG!")

    case _: HttpRequest => sender ! HttpResponse(status = 404, entity = "404 — Unknown resource!")

    case Timedout(HttpRequest(method, uri, _, _, _)) =>
      sender ! HttpResponse(status = 500, entity = "The " + method + " request to '" + uri + "' has timed out...")
  }
}