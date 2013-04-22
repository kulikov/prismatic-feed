package ru.kulikovd.prismaticfeed

import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import spray.client.pipelining._
import spray.http._
import spray.http.HttpHeaders.RawHeader
import spray.http.HttpHeaders.`Set-Cookie`
import spray.json._


case object GetAuthToken
case object LoadFeed
case object AuthExpired
case class UpdateAuthCookies(value: String)
case class FeedItems(items: Map[Long, JsObject])
case class AuthError(reason: String)


class PrismaticParser(username: String, password: String) extends Actor with ActorLogging {
  import context.dispatcher

  implicit val timeout = Timeout(10 seconds)

  var authCookies: Option[String] = None

  def receive = {
    case LoadFeed ⇒
      authCookies match {
        case Some(auth) ⇒ returnFeed(sender, auth)
        case None ⇒ updateAuthToken(sender)
      }

    case UpdateAuthCookies ⇒
      authinticate()
  }

  def returnFeed(client: ActorRef, auth: String) {
    sendReceive.apply(HttpRequest(
      uri = "http://api.getprismatic.com/news/home",
      headers = List(RawHeader("Cookie", auth))
    )) onComplete {
      case Success(res) ⇒
        log.info("Feed successfully loaded!")

        try {
          client ! FeedItems(parseFeed(res.entity.asString))
        } catch {
          case e: Exception ⇒
            log.error("Error parse feed response from Prismatic: {}", e)
            log.error("Reason: {}", e.getStackTraceString)
            log.error("Response: {}", res)
        }

      case Failure(e) ⇒
        log.info("Auth expired {}", e)
        updateAuthToken(client)
    }
  }

  def parseFeed(json: String): Map[Long, JsObject] =
    JsonParser(json).asJsObject.fields("docs") match {
      case JsArray(docs) ⇒ docs.collect({ case doc: JsObject ⇒
        doc.fields("id").asInstanceOf[JsNumber].value.toLong → doc
      }).toMap
    }

  def updateAuthToken(client: ActorRef) {
    self ? UpdateAuthCookies onComplete {
      case Success(auth: String) ⇒
        authCookies = Some(auth)
        returnFeed(client, auth)

      case other ⇒
        log.error("Auth error {}", other)
        client ! AuthError(other.toString)
    }
  }

  def authinticate() {
    val originalSender = sender

    /**
     * 1
     */
    sendReceive.apply(Get("http://auth.getprismatic.com/receiver.html")) onComplete {
      case Success(res) ⇒

        res.header[`Set-Cookie`] foreach { h ⇒
          val AWSELB = h.cookie.name +"="+ h.cookie.content

          /**
           * 2
           */
          sendReceive.apply(HttpRequest(
            method = HttpMethods.POST,
            uri = "http://auth.getprismatic.com/auth/event_public_dispatch?api-version=1.0&ignore=true&whitelist_url=http%3A%2F%2Fgetprismatic.com%2Fnews%2Fhome&soon_url=http%3A%2F%2Fgetprismatic.com%2Fwelcome&create_url=http%3A%2F%2Fgetprismatic.com%2Fcreateaccount&resetpassword_url=http%3A%2F%2Fgetprismatic.com%2Fresetpassword",
            headers = List(
              RawHeader("Cookie", AWSELB),
              RawHeader("Content-Type", "application/json"),
              RawHeader("Referer", "http://auth.getprismatic.com/receiver.html"),
              RawHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/27.0.1453.56 Safari/537.36")
            ),
            entity = HttpBody(ContentType.`application/json`, """{"category":"load","page":{"uri":"/","search":"","referer":"http://getprismatic.com/news/home"},"browser":"Chrome 27 (Mac)","type":"landing"}""")
          )) onComplete {
            case Success(res2) ⇒

              val pPublic = res2.headers.collect({
                case `Set-Cookie`(c) if (c.name == "p_public") ⇒ "p_public=" + c.content
              }).head

              val psWww = res2.headers.collect({
                case `Set-Cookie`(c) if (c.name == "_ps_www") ⇒ "_ps_www=" + c.content
              }).head


              /**
               * 3
               */
              sendReceive.apply(HttpRequest(
                method = HttpMethods.POST,
                uri = "http://auth.getprismatic.com/auth/login?api-version=1.0&ignore=true&whitelist_url=http%3A%2F%2Fgetprismatic.com%2Fnews%2Fhome&soon_url=http%3A%2F%2Fgetprismatic.com%2Fwelcome&create_url=http%3A%2F%2Fgetprismatic.com%2Fcreateaccount&resetpassword_url=http%3A%2F%2Fgetprismatic.com%2Fresetpassword",
                headers = List(
                  RawHeader("Cookie", List(AWSELB, pPublic, psWww) mkString "; "),
                  RawHeader("Content-Type", "application/json"),
                  RawHeader("Referer", "http://auth.getprismatic.com/receiver.html"),
                  RawHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/27.0.1453.56 Safari/537.36")
                ),
                entity = HttpBody(ContentType.`application/json`, """{"handle":"%s","password":"%s"}""".format(username, password))
              )) onComplete {
                case Success(res3) ⇒

                  val prismatic = res3.headers.collect({
                    case `Set-Cookie`(c) if (c.name == "prismatic") ⇒ "prismatic=" + c.content
                  }).head

                  /**
                   * 4
                   */
                  sendReceive.apply(HttpRequest(
                    method = HttpMethods.GET,
                    uri = "http://api.getprismatic.com/user/info?rand=21701246&callback=prismatic.userPromise.fulfill&api-version=1.0",
                    headers = List(
                      RawHeader("Cookie", List(pPublic, prismatic) mkString "; "),
                      RawHeader("Referer", "http://getprismatic.com/news/home"),
                      RawHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_7_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/27.0.1453.56 Safari/537.36")
                    )
                  )) onComplete {
                    case Success(res4) ⇒

                      val psApiAndAWSELB = res4.headers.collect({
                        case `Set-Cookie`(c) ⇒ c.name + "=" + c.content
                      }).mkString("; ")

                      originalSender ! psApiAndAWSELB
                  }
              }
          }
        }
    }
  }
}
