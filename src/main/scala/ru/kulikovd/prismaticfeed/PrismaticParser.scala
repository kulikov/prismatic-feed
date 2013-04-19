package ru.kulikovd.prismaticfeed

import scala.concurrent.duration._
import akka.util.Timeout
import akka.actor._
import spray.client.pipelining._
import spray.http.{HttpMethods, HttpHeader, HttpCookie, HttpRequest}
import spray.http.HttpHeaders.{RawHeader, Cookie}


case object ParseFeed


class PrismaticParser extends Actor with ActorLogging {
  import context.dispatcher

  implicit val timeout = Timeout(5 second)

  context.system.scheduler.schedule(3 seconds, 30 seconds, self, ParseFeed)

  def receive = {
    case ParseFeed =>
      log.info("Parsing feed")

      sendReceive.apply(HttpRequest(
        uri = "http://api.getprismatic.com/news/home",
        headers = List(RawHeader("Cookie", "_ps_api=23157__ioftlm0egsdfg75b2fosdfju76qrsdf6tka1h776v; AWSELB=1509F1AD0A09EB6sdf234CsgFi87601234ACC3B93041502497BC0E39494F3506234AA5503F5761CEDD243118BFF1D313B78B29E8AE943E7E24DCF3CA837E46AE60AED06EC9F553A166CA4CEC0D302C406E132sdFADB61F9AB8715FADC1ECB885F43BE3D"))
      )) onSuccess {
        case response ⇒ println("\n | " + response.entity.asString + "\n |\n")
      }
  }
}
