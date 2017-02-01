package controllers

import javax.inject._

import akka.NotUsed
import akka.actor._
import akka.event.Logging
import akka.pattern.ask
import akka.stream._
import akka.stream.scaladsl._
import akka.util.Timeout
import org.reactivestreams.Publisher
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
 * This class creates the actions and the websocket needed.
 */
@Singleton
class Application @Inject()()(implicit actorSystem: ActorSystem,
  mat: Materializer,
  ec: ExecutionContext) extends Controller {

  // Use a direct reference to SLF4J
  private val logger = org.slf4j.LoggerFactory.getLogger("controllers.Application")

  // Home page that renders template
  def index = Action { implicit request =>
    Ok(views.html.index())
  }
}
