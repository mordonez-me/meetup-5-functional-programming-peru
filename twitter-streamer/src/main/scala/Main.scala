import akka.actor.{ActorSystem, Props}
import scala.concurrent.ExecutionContext.Implicits.global
import actors.TwitterStreamerActor

object Main{

  def main(args:Array[String]) = {

    implicit val system = ActorSystem("Meetup5Streamer")
    val actor = system.actorOf(Props(new TwitterStreamerActor()), "streamer")
    
  }
}