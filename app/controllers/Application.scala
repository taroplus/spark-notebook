package controllers

import javax.inject._

import akka.actor._
import akka.stream._
import play.api.mvc._

import scala.concurrent.ExecutionContext

/**
 * main play application controller
 */
@Singleton
class Application @Inject()()(implicit actorSystem: ActorSystem,
  mat: Materializer,
  ec: ExecutionContext) extends Controller {

  // Home page that renders template
  def index = Action { implicit request =>
    Ok(views.html.tree())
  }
}
