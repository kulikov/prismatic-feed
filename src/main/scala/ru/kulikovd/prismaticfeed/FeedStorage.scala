package ru.kulikovd.prismaticfeed

import scala.collection.immutable.TreeMap
import scala.concurrent.{Promise, Future}
import scala.concurrent.duration._
import scala.util.{Success, Failure}

import akka.actor.{ActorLogging, Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout


case class SortedFeedItems(items: TreeMap[Long, FeedItem])
case class UpdateFeed(key: String, msg: AnyRef)


class FeedStorage(parser: ActorRef, updateInterval: FiniteDuration) extends Actor with ActorLogging {
  import context.dispatcher

  implicit val timeout = Timeout(15 seconds)
  implicit val ordering = Ordering.ordered[Long].reverse

  var feeds = collection.mutable.Map.empty[String, TreeMap[Long, FeedItem]]

  def receive = {
    case msg @ GetPrivateFeed ⇒ prepare(sender, "private")(msg)
    case msg @ GetPublicActivityFor(user) ⇒ prepare(sender, "public/" + user)(msg)
    case UpdateFeed(key, msg) ⇒ updateFeed(key, msg)
  }

  /**
   * If hasn't feed with name $key - request it from PrismaticParser, or return
   */
  def prepare(client: ActorRef, key: String)(msg: AnyRef) {
    feeds.get(key) map (Future.successful) getOrElse (updateFeed(key, msg)) onComplete {
      case Success(items) ⇒
        client ! SortedFeedItems(items)

      case Failure(e) ⇒
        log.error(e.getMessage)
        client ! e
    }
  }

  /**
   * Fetch new items for feed with name $key
   */
  def updateFeed(key: String, msg: AnyRef) = {
    val p = Promise[TreeMap[Long, FeedItem]]()

    parser ? msg onComplete {
      case Success(FeedItems(items)) ⇒
        feeds.update(key, (feeds.getOrElse(key, TreeMap.empty[Long, FeedItem]) ++ items).takeRight(20))
        p.success(feeds(key))
        context.system.scheduler.scheduleOnce(updateInterval, self, UpdateFeed(key, msg))

      case error ⇒
        p.failure(new Exception(s"Error request $key feed by $msg. Reason: $error"))
    }

    p.future
  }
}
