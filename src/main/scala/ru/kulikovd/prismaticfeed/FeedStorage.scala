package ru.kulikovd.prismaticfeed

import scala.collection.immutable.TreeMap
import scala.concurrent.duration._
import scala.util.{Success, Failure}

import akka.actor.{ActorLogging, Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import spray.json.JsObject


case object GetFeed
case object UpdateFeed
case class SortedFeedItems(items: TreeMap[Long, JsObject])


class FeedStorage(parser: ActorRef, updateInterval: FiniteDuration) extends Actor with ActorLogging {
  import context.dispatcher

  implicit val timeout = Timeout(15 seconds)

  var feed = TreeMap.empty[Long, JsObject]

  def receive = {
    case GetFeed ⇒
      if (feed.isEmpty) updateFeed(Some(sender))
      else sender ! SortedFeedItems(feed)

    case UpdateFeed ⇒ updateFeed()
  }

  def updateFeed(client: Option[ActorRef] = None) {
    parser ? LoadFeed onComplete {
      case Success(FeedItems(items)) ⇒
        feed ++= items
        feed = feed.takeRight(30)
        client foreach (_ ! SortedFeedItems(feed))
        context.system.scheduler.scheduleOnce(updateInterval, self, UpdateFeed)

      case error ⇒
        log.error("Error {}!", error)
        context.system.scheduler.scheduleOnce(3 minutes, self, UpdateFeed) // retry after timeout
    }
  }
}
