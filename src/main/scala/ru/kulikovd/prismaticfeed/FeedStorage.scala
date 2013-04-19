package ru.kulikovd.prismaticfeed

import scala.concurrent.duration._
import scala.util.{Success, Failure}

import akka.actor.{ActorLogging, Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout


case object GetFeed
case object UpdateFeed


class FeedStorage(parser: ActorRef) extends Actor with ActorLogging {
  import context.dispatcher

  implicit val timeout = Timeout(15 seconds)

  var feed: Option[String] = None

  def receive = {
    case GetFeed ⇒ feed match {
      case Some(text) ⇒ sender ! FeedResult(text)
      case None ⇒
        val originalSender = sender

        parser ? LoadFeed onComplete {
          case Success(result: FeedResult) ⇒
            feed = Some(result.value)
            originalSender ! result

          case Failure(e) ⇒
            log.error("Error {}!", e)
        }
    }

    case UpdateFeed ⇒
      feed = None
      self ? GetFeed onComplete {
        case Success(_) ⇒ context.system.scheduler.scheduleOnce(1 hour, self, UpdateFeed)
        case Failure(_) ⇒ context.system.scheduler.scheduleOnce(3 minutes, self, UpdateFeed)
      }
  }
}
