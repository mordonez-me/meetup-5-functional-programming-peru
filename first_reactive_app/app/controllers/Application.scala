package controllers

import java.nio.file.Paths
import javax.inject.Inject

import actors.UserSocketActor
import akka.actor._
import akka.stream._
import play.api._
import play.api.http.HttpEntity
import play.api.libs.json._
import play.api.mvc._
import play.api.libs.streams._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._


class Application @Inject() (implicit system: ActorSystem, materializer: Materializer) extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def tweetsSocket = WebSocket.accept[JsValue, JsValue] { request =>

    ActorFlow.actorRef(out => UserSocketActor.props(out))
  }
}