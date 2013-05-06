package ru.kulikovd.prismaticfeed

import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

import akka.actor.{ActorRef, Actor}
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http
import spray.can.server.Stats
import spray.http._
import spray.routing.HttpService


class RssService(feedStorage: ActorRef) extends Actor with HttpService {
  import context.dispatcher

  implicit val timeout = Timeout(20 seconds)

  def actorRefFactory = context

  def receive = runRoute {
    get {
      path("feed") {
        respondWithMediaType(MediaTypes.`text/xml`) {
          complete { feed(GetPrivateFeed, "Prismatic RSS feed") }
        }
      } ~
      path("public" / "[A-z0-9-_]+".r) { case user: String ⇒
        respondWithMediaType(MediaTypes.`text/xml`) {
          complete { feed(GetPublicActivityFor(user), s"${user.capitalize}'s Prismatic activity") }
        }
      } ~
      path("stats") {
        complete {
          context.actorFor("/user/IO-HTTP/listener-0") ? Http.GetStats map {
            case stats: Stats ⇒
              s"""
              | Uptime                : ${Duration(stats.uptime.toHours, TimeUnit.HOURS)}
              | Total requests        : ${stats.totalRequests}
              | Open requests         : ${stats.openRequests}
              | Max open requests     : ${stats.maxOpenRequests}
              | Total connections     : ${stats.totalConnections}
              | Open connections      : ${stats.openConnections}
              | Max open connections  : ${stats.maxOpenConnections}
              | Requests timed out    : ${stats.requestTimeouts}
              """.trim.stripMargin
          }
        }
      }
    }
  }

  def feed(msg: FeedRequest, title: String) = feedStorage ? msg collect {
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

  private def cdata(s: String) = xml.PCData(s.replaceAll("\\n", "<br/>"))
}
