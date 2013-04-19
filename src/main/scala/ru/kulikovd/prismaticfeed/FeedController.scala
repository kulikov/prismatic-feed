package ru.kulikovd.prismaticfeed

import scala.collection.immutable.TreeMap
import scala.concurrent.duration._
import scala.util.Success
import scala.xml.PCData

import akka.actor.{ActorRef, ActorLogging, Actor}
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http
import spray.http._
import spray.http.HttpMethods._
import spray.json.{JsString, JsValue, JsObject}


class FeedController(feedGenerator: ActorRef) extends Actor with ActorLogging {
  import context.dispatcher

  implicit val timeout = Timeout(20 seconds)

  def receive = LoggingReceive {
    case _: Http.Connected => sender ! Http.Register(self)

    case HttpRequest(GET, Uri.Path("/feed"), _, _, _) =>
      val originalSender = sender

      feedGenerator ? GetFeed onComplete {
        case Success(SortedFeedItems(items)) ⇒
          originalSender ! HttpResponse(entity =
            HttpBody(ContentType(MediaTypes.`application/rss+xml`, HttpCharsets.`UTF-8`),
            generateRss(items).toString()
          ))

        case other ⇒
          originalSender ! HttpResponse(status = 500, entity = other.toString)
      }

    case r: HttpRequest => sender ! HttpResponse(status = 404, entity = s"404 — Unknown resource ${r.uri.path}!")

    case Timedout(HttpRequest(method, uri, _, _, _)) =>
      sender ! HttpResponse(status = 500, entity = s"The $method request to '$uri' has timed out...")
  }

  implicit class JsonString(val json: JsValue) {
    def s: String = json match {
      case JsString(value) ⇒ value
      case other ⇒ other.toString()
    }

    def cdata: PCData = xml.PCData(s)

    def date = DateTime(s.toLong).toRfc1123DateTimeString
  }

  def generateRss(items: TreeMap[Long, JsObject]) =
    <rss version="2.0">
    <channel>
    <title>Prismatic RSS feed</title>
    <link>http://kulikovd.ru/prismatic/feed</link>
    {
      items map { case (id, JsObject(doc)) ⇒
        <item>
          <title>{doc("title").cdata}</title>
          <link>{doc("url").s}</link>
          <guid isPermaLink="false">{doc("id").s}</guid>
          <pubDate>{doc("date").date}</pubDate>
          <description>{doc("text").cdata}</description>
        </item>
      }
    }
    </channel>
    </rss>
}
