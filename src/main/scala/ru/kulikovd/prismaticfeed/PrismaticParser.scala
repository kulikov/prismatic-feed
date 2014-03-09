package ru.kulikovd.prismaticfeed

import scala.concurrent.{Promise, Future}
import scala.concurrent.duration._
import scala.util.Success

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import spray.client.pipelining._
import spray.http._
import spray.http.HttpHeaders.RawHeader
import spray.http.HttpHeaders.`Set-Cookie`
import akka.io.IO
import spray.can.Http


sealed trait FeedRequest
case object GetPrivateFeed extends FeedRequest
case class GetPublicActivityFor(username: String) extends FeedRequest

case class FeedItems(feedId: String, items: Map[Long, FeedItem])
case class FeedItem(id: Long, title: String, url: String, date: Long, author: String, text: String)

case class PrismaticJson(id: String, docs: List[DocJson])
case class AuthorJson(name: Option[String])
case class FeedJson(title: Option[String])
case class DocJson(
  id: Long,
  title: String,
  url: String,
  date: Long,
  author: Option[AuthorJson],
  feed: Option[FeedJson],
  text: String
)

case class ViewedJson(`viewed-data`: Iterable[ViewedDocJson])
case class ViewedDocJson(`doc-id`: Long, `feed-id`: String, dwell: Int = 0)


class PrismaticParser(username: String, password: String) extends Actor with ActorLogging with JsonSupport {
  import context.dispatcher
  import context.system

  implicit val timeout = Timeout(10 seconds)

  var authCookies: Option[String] = None

  val randomizer = new scala.util.Random

  def receive = {
    case GetPrivateFeed             ⇒ request(sender)(privateFeed)
    case GetPublicActivityFor(user) ⇒ request(sender)(publicActivity(user))
  }

  /**
   * Private user feed
   */
  def privateFeed(client: ActorRef, auth: String) =
    (IO(Http) ? HttpRequest(
      uri = "http://api.getprismatic.com/news/personal/personalkey?api-version=1.2&limit=8&rand=" + randomizer.nextInt(10000000),
      headers = List(RawHeader("Cookie", auth))
    )).mapTo[HttpResponse]

  /**
   * Public activity for $user
   */
  def publicActivity(user: String)(client: ActorRef, auth: String) =
    (IO(Http) ? HttpRequest(
      uri = "http://api.getprismatic.com/news/activity/" + user + "?api-version=1.2&limit=8&rand=" + randomizer.nextInt(10000000),
      headers = List(RawHeader("Cookie", auth))
    )).mapTo[HttpResponse]


  private def request(client: ActorRef)(ff: (ActorRef, String) ⇒ Future[HttpResponse]) {
    (authCookies map Future.successful getOrElse authinticate) onComplete {
      case Success(auth: String) ⇒
        authCookies = Some(auth)

        ff(client, auth) onComplete {
          case Success(HttpResponse(StatusCodes.OK, entity, _, _)) ⇒
            log.info("Feed successfully loaded!")

            try {
              val items = parseResults(entity.asString)
              client ! items

              // mark all items as viewed
              IO(Http) ? Post(
                "http://api.getprismatic.com/event/mark-viewed?api-version=1.2",
                HttpEntity(ContentTypes.`application/json`, serialize(ViewedJson(items.items.map { case (id, _) ⇒
                  ViewedDocJson(id, items.feedId)
                })))
              ).withHeaders(List(
                RawHeader("Cookie", auth)
              )) onSuccess {
                case HttpResponse(StatusCodes.OK, _, _, _) ⇒
                  log.info("Marked items {} as viewed", items.items.keys.mkString(", "))
              }
            } catch {
              case e: Exception ⇒
                log.error("Error parse feed response from Prismatic: {} \n {}", e, e.getStackTraceString)
                log.error("Response: {}", entity)
            }

          case Success(HttpResponse(StatusCodes.Forbidden, entity, _, _)) ⇒
            log.info("Prismatic's forbidden: {}", entity)
            authCookies = None
            request(client)(ff) // retry again with new auth cookies

          case other ⇒
            log.error("Prismatic server error: {}", other)
            client ! other
        }

      case authError ⇒
        log.warning("Auth error! Try again after timeout. {}", authError)
        authCookies = None
        context.system.scheduler.scheduleOnce(5 seconds)(request(client)(ff))
    }
  }

  /**
   * Parse json string
   */
  private def parseResults(json: String) = {
    val feed = deserialize[PrismaticJson](json)

    FeedItems(
      feedId = feed.id,
      items  = feed.docs.map({ doc ⇒
        doc.id → FeedItem(
          id = doc.id,
          title = doc.title,
          url = doc.url,
          date = doc.date,
          author = Seq(doc.author.flatMap(_.name), doc.feed.flatMap(_.title)).flatten.mkString(" / "),
          text = doc.text
        )
      }).toMap
    )
  }


  /**
   * Gat auth cookies form Prismatic
   */
  private def authinticate: Future[String] = {
    val promise = Promise[String]()

    def cookie(headers: List[HttpHeader], cookieName: String) = headers.collectFirst({
      case `Set-Cookie`(c) if c.name == cookieName ⇒ cookieName + "=" + c.content
    }).getOrElse(throw new RuntimeException("Not found cookie with name " + cookieName))

    IO(Http) ? Post(
      "http://api.getprismatic.com/2.0/auth/login?api-version=1.2",
      HttpEntity(ContentTypes.`application/json`, """{"handle":"%s","password":"%s"}""".format(username, password))
    ) flatMap {
      case HttpResponse(StatusCodes.OK, _, headers, _) ⇒
        val prismatic = cookie(headers, "prismatic")

        IO(Http) ? Post(
          "http://api.getprismatic.com/news/personal/personalkey?api-version=1.2",
          HttpEntity(ContentTypes.`application/json`, """{"skip-ids":null,"viewed-data":null}""")
        ).withHeaders(List(
          RawHeader("Cookie", prismatic)
        )) map (r ⇒ (prismatic, r))
    } onComplete {
      case Success((prismatic: String, HttpResponse(StatusCodes.OK, _, headers, _))) ⇒
        log.info("Auth complete!")
        promise.success(prismatic + "; " + cookie(headers, "_ps_api") + "; " + cookie(headers, "AWSELB"))

      case error ⇒
        promise.failure(new Exception("Failure get auth cookies " + error))
    }

    promise.future
  }
}
