package ru.kulikovd.prismaticfeed

import scala.concurrent.duration._
import scala.util.{Success, Failure}

import akka.actor.{ActorLogging, Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout


case object GetFeed
case object UpdateFeed


class FeedStorage(parser: ActorRef, updateInterval: FiniteDuration) extends Actor with ActorLogging {
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
            context.system.scheduler.scheduleOnce(updateInterval, self, UpdateFeed)

          case Failure(e) ⇒
            log.error("Error {}!", e)
            context.system.scheduler.scheduleOnce(3 minutes, self, UpdateFeed) // retry after timeout
        }
    }

    case UpdateFeed ⇒
      feed = None // invalidate cache
      self ! GetFeed
  }
}
