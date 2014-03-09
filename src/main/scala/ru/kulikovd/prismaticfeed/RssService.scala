package ru.kulikovd.prismaticfeed

import scala.concurrent.duration._

import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import spray.http._
import spray.routing.HttpService


class RssService(feedStorage: ActorRef) extends Actor with HttpService {
  import context.dispatcher

  implicit val timeout = Timeout(20 seconds)

  def actorRefFactory = context

  def receive = runRoute {
    respondWithMediaType(MediaTypes.`text/xml`) {
      get {
        path("feed") {
          complete(feed(GetPrivateFeed, "Prismatic RSS feed"))
        } ~
        path("public" / "[A-z0-9-_]+".r) { user ⇒
          complete(feed(GetPublicActivityFor(user), s"${user.capitalize}'s Prismatic activity"))
        }
      }
    }
  }

  private def feed(msg: FeedRequest, title: String) = (feedStorage ? msg) map {
    case SortedFeedItems(items) ⇒
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
      <rss version="2.0">
        <channel>
        <title>{cdata(title)}</title>
        {
          items map { case (_, item) ⇒
            <item>
              <title>{cdata(item.title)}</title>
              <link>{item.url}</link>
              <guid isPermaLink="true">{item.url}</guid>
              <pubDate>{DateTime(item.date).toRfc1123DateTimeString}</pubDate>
              <author>{item.author}</author>
              <description>{cdata(item.text)}</description>
            </item>
          }
        }
        </channel>
      </rss>

    case error ⇒
      <error>{cdata(error.toString)}</error>.toString()
  }

  private def cdata(s: String) =
    xml.PCData(s.replaceAll("\\n", "<br/>"))
}
