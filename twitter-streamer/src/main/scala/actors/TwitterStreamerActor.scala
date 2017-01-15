package actors

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, ActorLogging, Terminated}
import akka.actor.Actor.Receive
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Framing, Source, Sink}
import akka.util.ByteString
import akka.http.scaladsl.model._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.Http
import akka.routing.{ ActorRefRoutee, BroadcastRoutingLogic, Router }

import com.hunorkovacs.koauth.service.consumer.DefaultConsumerService
import com.hunorkovacs.koauth.domain.OauthParams._
import com.hunorkovacs.koauth.domain.KoauthRequest

import play.api.libs.json._

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Success, Failure}
import com.typesafe.config.ConfigFactory

/**
  * Created by marco on 1/11/17.
  */

object TwitterStreamerActor {

  def connectToTwitter(actor:ActorRef)(implicit dispatcher:ExecutionContext, actorSystem:ActorSystem, materializer:ActorMaterializer) = {

    val conf = ConfigFactory.load()
    
    val apiKey = conf.getString("twitter.apiKey")
    val apiSecret = conf.getString("twitter.apiSecret")
    val token = conf.getString("twitter.token")
    val tokenSecret = conf.getString("twitter.tokenSecret")
    val url = "https://stream.twitter.com/1.1/statuses/filter.json"
    val body = "track=cat"

    val consumer = new DefaultConsumerService(dispatcher)

    val oauthHeader = consumer.createOauthenticatedRequest(
      KoauthRequest(
        method = "POST",
        url = url,
        authorizationHeader = None,
        body = Some(body)
      ),
      apiKey,
      apiSecret,
      token,
      tokenSecret
    ) map (_.header)

    oauthHeader.onComplete {
      case Success(header) =>
        val httpHeaders: List[HttpHeader] = List(
          HttpHeader.parse("Authorization", header) match {
            case ParsingResult.Ok(h, _) => Some(h)
            case _ => None
          },
          HttpHeader.parse("Accept", "*/*") match {
            case ParsingResult.Ok(h, _) => Some(h)
            case _ => None
          }
        ).flatten

        val httpRequest: HttpRequest = HttpRequest(
          method = HttpMethods.POST,
          uri = Uri(url),
          headers = httpHeaders,
          entity = HttpEntity(contentType = ContentType(MediaTypes.`application/x-www-form-urlencoded`, HttpCharsets.`UTF-8`), string = body)
        )
        val request = Http().singleRequest(httpRequest)

        val source = 
        Source.fromFuture(request)
        .flatMapConcat(_.entity.dataBytes)
        .via(Framing.delimiter(
          ByteString.fromString("\n"), 
          maximumFrameLength = 40000, 
          allowTruncation = false
        ))
        .map{ byteString =>
          Json.parse(byteString.utf8String)
        }.runWith(Sink.actorRef(actor, ""))
        
      case Failure(failure) => println(failure.getMessage)
    }
  }
}

class TwitterStreamerActor() extends Actor with ActorLogging{

  var router = Router(BroadcastRoutingLogic(), Vector.empty)

  override def preStart(): Unit = {
    log.info("TwitterStreamerActor has started")
    implicit val ec = context.dispatcher
    implicit val materializer = ActorMaterializer()
    implicit val system = context.system
    TwitterStreamerActor.connectToTwitter(self)
  }

  override def postStop(): Unit = {
    log.info("TwitterStreamerActor has finished")
  }

  def receive = {
    case "subscribe-to-main-stream" => {
      log.info("receiving request to subscribe")
      context watch sender
      router = router.addRoutee(sender)
      sender ! "you-are-subscribed"
    }

    case Terminated(actorRef) => {
      log.info("remote subscriber has been terminated")
      context unwatch actorRef
      router = router.removeRoutee(actorRef)
    }

    case tweet:JsObject => {
      router.route(tweet, self)
    }
  }


}
