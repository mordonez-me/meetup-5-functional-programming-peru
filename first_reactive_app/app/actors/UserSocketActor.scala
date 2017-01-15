package actors

import akka.actor._

import play.api.libs.json._

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

/**
  * Created by marco on 1/11/17.
  */

object UserSocketActor {
  def props(out: ActorRef) = Props(new UserSocketActor(out))
}

case class UserSocketActor(out:ActorRef) extends Actor with ActorLogging{

  import context.dispatcher 

  val conf = ConfigFactory.load()
  val remoteActorPath = s"akka.tcp://Meetup5Streamer@${conf.getString("streamerNode")}/user/streamer"
  val remoteStreamer = context.actorSelection(remoteActorPath)
  var isSubscribed = false
  var firstConnection = true

  context.system.scheduler.schedule(30 seconds, 30 seconds) {
    if(isSubscribed == false)
      println("ask for subscribe-to-main-stream")
      remoteStreamer ! "subscribe-to-main-stream"
  }

  context.system.scheduler.schedule(5 seconds, 10 seconds) {
    if(firstConnection)
      out ! Json.obj("error" -> "not-connected", "message" -> "El servidor streamer no responde, estamos trabajando duro para solucionarlo.")
  }

  def receive = {

    case m:JsObject => out ! m

    case Terminated(actor) => 
      out ! Json.obj("error" -> "disconneted", "message" -> "Se ha desconectado el streamer, estamos trabajando duro para solucionarlo.")

    case "you-are-subscribed" => 
      println("this actor has been subscribed")
      context watch sender
      isSubscribed = true
      firstConnection = false
  }

  override def preStart() = {
    remoteStreamer ! "subscribe-to-main-stream"
  }

}
