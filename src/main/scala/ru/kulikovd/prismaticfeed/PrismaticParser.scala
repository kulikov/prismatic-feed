package ru.kulikovd.prismaticfeed

import scala.concurrent.{Promise, Future}
import scala.concurrent.duration._
import scala.util.Success

import akka.actor._
import akka.util.Timeout
import spray.client.pipelining._
import spray.http._
import spray.http.HttpHeaders.RawHeader
import spray.http.HttpHeaders.`Set-Cookie`
import spray.json._


sealed trait FeedRequest
case object GetPrivateFeed extends FeedRequest
case class GetPublicActivityFor(username: String) extends FeedRequest

case class FeedItems(items: Map[Long, FeedItem])
case class FeedItem(id: Long, title: String, url: String, date: Long, author: String, text: String)


class PrismaticParser(username: String, password: String) extends Actor with ActorLogging {
  import context.dispatcher

  implicit val timeout = Timeout(10 seconds)

  var authCookies: Option[String] = None

  def receive = {
    case GetPrivateFeed             ⇒ request(sender)(privateFeed)
    case GetPublicActivityFor(user) ⇒ request(sender)(publicActivity(user))
  }

  /**
   * Private user feed
   */
  def privateFeed(client: ActorRef, auth: String) =
    sendReceive.apply(HttpRequest(
      uri = "http://api.getprismatic.com/news/home",
      headers = List(RawHeader("Cookie", auth))
    ))

  /**
   * Public activity for $user
   */
  def publicActivity(user: String)(client: ActorRef, auth: String) =
    sendReceive.apply(HttpRequest(
      uri = "http://api.getprismatic.com/news/activity/" + user,
      headers = List(RawHeader("Cookie", auth))
    ))


  private def request(client: ActorRef)(ff: (ActorRef, String) ⇒ Future[HttpResponse]) {
    authCookies map (Future.successful) getOrElse authinticate onComplete {
      case Success(auth: String) ⇒
        authCookies = Some(auth)
        ff(client, auth) onComplete {
          case Success(HttpResponse(StatusCodes.OK, entity, _, _)) ⇒
            log.info("Feed successfully loaded!")

            try {
              client ! FeedItems(parseResults(entity.asString))
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

  implicit private class JsonExtractors(val json: JsValue) {
    def s: String = json match {
      case JsString(value) ⇒ value
      case other ⇒ other.toString()
    }

    def n: Long = json match {
      case JsNumber(value) ⇒ value.toLong
      case other ⇒ 0L
    }

    def f(name: String) = json match {
      case JsObject(fields) if (fields.contains(name)) ⇒ fields(name).s
      case _ ⇒ ""
    }
  }

  /**
   * Parse json string
   */
  private def parseResults(json: String): Map[Long, FeedItem] =
    JsonParser(json).asJsObject.fields("docs") match {
      case JsArray(docs) ⇒
        docs.collect({ case JsObject(fs) ⇒
          FeedItem(
            id     = fs("id").n,
            title  = fs("title").s,
            url    = fs("url").s,
            date   = fs("date").n,
            author = fs("author").f("name") + " / " + fs("feed").f("title"),
            text   = fs("text").s
          )
        }).map(f ⇒ f.id → f).toMap

      case other ⇒
        log.error("Malformed json format {}", other)
        Map.empty
    }


  /**
   * Gat auth cookies form Prismatic
   */
  private def authinticate: Future[String] = {
    val promise = Promise[String]()

    def cookie(headers: List[HttpHeader], cookieName: String) = headers.collect({
      case `Set-Cookie`(c) if (c.name == cookieName) ⇒ cookieName + "=" + c.content
    }).head

    var AWSELB, pPublic = ""

    sendReceive.apply(Get("http://auth.getprismatic.com/receiver.html")) flatMap {
      case HttpResponse(StatusCodes.OK, _, headers, _) ⇒
        AWSELB = cookie(headers, "AWSELB")

        sendReceive apply Post(
          "http://auth.getprismatic.com/auth/event_public_dispatch?api-version=1.0",
          HttpEntity(ContentType.`application/json`, """{"category":"load","page":{"uri":"/","search":"","referer":""},"browser":"","type":"landing"}""")
        ).withHeaders(List(
          RawHeader("Cookie", AWSELB)
        ))

    } flatMap {
      case HttpResponse(StatusCodes.OK, _, headers, _) ⇒
        pPublic = cookie(headers, "p_public")
        val psWww = cookie(headers, "_ps_www")

        sendReceive apply Post(
          "http://auth.getprismatic.com/auth/login?api-version=1.0&ignore=true&whitelist_url=http%3A%2F%2Fgetprismatic.com%2Fnews%2Fhome",
          HttpEntity(ContentType.`application/json`, """{"handle":"%s","password":"%s"}""".format(username, password))
        ).withHeaders(List(
          RawHeader("Cookie", List(AWSELB, pPublic, psWww) mkString "; ")
        ))

    } flatMap {
      case HttpResponse(StatusCodes.OK, _, headers, _) ⇒
        val prismatic = cookie(headers, "prismatic")

        sendReceive apply Get(
          "http://api.getprismatic.com/user/info?rand=21701246&callback=prismatic.userPromise.fulfill&api-version=1.0"
        ).withHeaders(List(
          RawHeader("Cookie", List(pPublic, prismatic) mkString "; ")
        ))

    } onComplete {
      case Success(HttpResponse(StatusCodes.OK, _, headers, _)) ⇒
        log.info("Auth complete!")
        promise.success(cookie(headers, "_ps_api") + "; " + cookie(headers, "AWSELB"))

      case error ⇒
        promise.failure(new Exception("Failure get auth cookies " + error))
    }

    promise.future
  }
}
