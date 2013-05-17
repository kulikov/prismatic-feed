package ru.kulikovd.prismaticfeed

import scala.collection.immutable.TreeMap
import scala.concurrent.{Promise, Future}
import scala.concurrent.duration._
import scala.util.{Success, Failure}

import akka.actor.{Stash, ActorLogging, Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout


case class SortedFeedItems(items: TreeMap[Long, FeedItem])


class FeedStorage(parser: ActorRef, updateInterval: FiniteDuration) extends Actor with Stash with ActorLogging {
  import context.dispatcher

  import FeedStorageProtocol._

  implicit val timeout = Timeout(15 seconds)
  implicit val ordering = Ordering.ordered[Long].reverse

  val MaxItemsByFeed = 30

  var feeds = collection.mutable.Map.empty[FeedRequest, TreeMap[Long, FeedItem]]

  def receive = {
    case msg: FeedRequest ⇒ prepare(sender, msg)
    case UpdateFeed(msg)  ⇒ updateFeed(msg)
  }

  /**
   * If hasn't feed for $msg - request it from PrismaticParser, or return
   */
  def prepare(client: ActorRef, msg: FeedRequest) {
    feeds.get(msg) map (Future.successful) getOrElse (updateFeed(msg)) onComplete {
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
  def updateFeed(msg: FeedRequest) = {
    val p = Promise[TreeMap[Long, FeedItem]]()

    log.info("Request feed by {}", msg)

    def complete() {
      context.system.scheduler.scheduleOnce(updateInterval, self, UpdateFeed(msg))
      context.unbecome()
      unstashAll()
    }

    context.become {
      case UpdateComplete(items) ⇒
        feeds.update(msg, (feeds.getOrElse(msg, TreeMap.empty[Long, FeedItem]) ++ items).take(MaxItemsByFeed))
        p.success(feeds(msg))
        complete()

      case UpdateFailure(reason) ⇒
        p.failure(reason)
        complete()

      case _ ⇒ stash()
    }

    parser ? msg onComplete {
      case Success(FeedItems(items)) ⇒ self ! UpdateComplete(items)
      case error ⇒ self ! UpdateFailure(new Exception(s"Error request feed by $msg. Reason: $error"))
    }

    p.future
  }
}


private object FeedStorageProtocol {
  case class UpdateFeed(msg: FeedRequest)
  case class UpdateComplete(items: Map[Long, FeedItem])
  case class UpdateFailure(reason: Throwable)
}
