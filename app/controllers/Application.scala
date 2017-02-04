package controllers

import java.io.File
import java.net.URLDecoder
import javax.inject.Inject

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.ConfigRenderOptions
import play.api.Configuration
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.{Action, Controller}

import scala.concurrent.ExecutionContext

/**
 * main application controller
 */
class Application @Inject()(configuration: Configuration)(implicit actorSystem: ActorSystem,
  mat: Materializer,
  ec: ExecutionContext) extends Controller {

  private final val notebookHome = new File(configuration.getString("notebook.home").getOrElse("."))

  // kernelspecs is always same
  private final val kernelSpecs = Json.obj(
    "default" -> "scala",
    "kernelspecs" -> Json.obj(
      "scala" -> Json.obj(
        "name" -> "scala",
        "spec" -> Json.obj(
          "language" -> "scala",
          "display_name" -> "Spark"
        ),
        "resources" -> Json.obj()),
      "python" -> Json.obj(
        "name" -> "python",
        "spec" -> Json.obj(
          "language" -> "python",
          "display_name" -> "PySpark"
        ),
        "resources" -> Json.obj())
    )
  )

  def kernelspecs() = Action { Ok(kernelSpecs) }

  // this shares the single kernel, so there's
  // no such thing as session
  def getSessions = Action { Ok(JsArray()) }

  def config(app: String) = Action {
    val configValue = configuration.getObject(s"$app")
    Ok(configValue match {
      case Some(cfg) => Json.parse(
        cfg.render(ConfigRenderOptions.concise().setJson(true)))
      case None => Json.obj()
    })
  }

  // Home page that renders template
  def tree(path: String) = Action { implicit request =>
    // prepare for breadcrumb
    val requestPath = toFile(path).toPath.normalize()
    val basePath = notebookHome.toPath.normalize()
    val breadcrumbs = if (basePath.equals(requestPath)) {
      Seq()
    } else {
      val relativePath = basePath.relativize(requestPath)
      (0 until relativePath.getNameCount)
        .map(i => {
          val subpath = relativePath.subpath(0, i + 1)
          (subpath.getName(i).toString, routes.Application.tree(subpath.toString).url)
        })
    }
    Ok(views.html.tree(Option(path).getOrElse("/"), breadcrumbs))
  }

  def contents(path: String, contentType: String) = Action { request =>
    val file = toFile(path)
    if (file.isDirectory) {
      val homePath = notebookHome.toPath.normalize()
      val files = file.listFiles
        .filter(!_.getName.startsWith("."))
        .map { f =>
          val relativePath = homePath.relativize(f.toPath)
          val contentType = f.getName match {
            case name: String if name.endsWith(".ipynb") => "notebook"
            case name: String if f.isDirectory => "directory"
            case _ => "file"
          }
        Json.obj("type" -> contentType, "name" -> f.getName, "path" -> relativePath.toString)
      }
      Ok(Json.obj("content" -> files))
    } else {
      Ok(Json.obj())
    }
  }

  private def toFile(path: String): File = {
    new File(notebookHome, Option(path)
        .map(p => URLDecoder.decode(p, "UTF-8"))
        .getOrElse("."))
  }
}
