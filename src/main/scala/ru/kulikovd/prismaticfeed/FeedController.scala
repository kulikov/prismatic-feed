package ru.kulikovd.prismaticfeed

import scala.concurrent.duration._
import scala.util.Success

import akka.actor.{ActorRef, ActorLogging, Actor}
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http
import spray.http.HttpMethods._
import spray.http._


class FeedController(feedGenerator: ActorRef) extends Actor with ActorLogging {
  import context.dispatcher

  implicit val timeout = Timeout(20 seconds)

  def receive = LoggingReceive {
    case _: Http.Connected => sender ! Http.Register(self)

    case HttpRequest(GET, Uri.Path("/feed"), _, _, _) =>
      val originalSender = sender

      feedGenerator ? GetFeed onComplete {
        case Success(FeedResult(text)) ⇒
          originalSender ! HttpResponse(entity = HttpBody(ContentType(MediaTypes.`application/rss+xml`, HttpCharsets.`UTF-8`), text))

        case other ⇒
          originalSender ! HttpResponse(status = 500, entity = other.toString)
      }

    case _: HttpRequest => sender ! HttpResponse(status = 404, entity = "404 — Unknown resource!")

    case Timedout(HttpRequest(method, uri, _, _, _)) =>
      sender ! HttpResponse(status = 500, entity = s"The $method request to '$uri' has timed out...")
  }
}
