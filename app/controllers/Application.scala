package controllers

import java.io.File
import java.net.URLDecoder
import java.util.UUID
import javax.inject.{Inject, Singleton}

import com.typesafe.config.ConfigRenderOptions
import play.api.Configuration
import play.api.libs.json.{JsArray, JsObject, JsString, Json}
import play.api.mvc.{Action, Controller}
import taroplus.contents.Contents
import taroplus.kernel.KernelAccess

import scala.util.Try

/**
 * main application controller
 */
@Singleton
class Application @Inject()(
    conf: Configuration,
    kernel: KernelAccess) extends Controller {

  private final val baseUrl = "/"
  private final val notebookHome = new File(conf.getString("notebook.home").getOrElse("."))
  // max notebook size
  private final val maxLength = 10 * 1024 * 1024 // 10M

  // serve /api/kernelspecs
  def kernelspecs() = Action { Ok(kernel.kernelSpecs) }

  // this shares the single kernel, so there's
  // no such thing as session
  def getSessions = Action { Ok(JsArray()) }

  // new session
  def newSession = Action(parse.tolerantJson) { request =>
    val json = request.body.as[JsObject] +
      ("id" -> JsString(UUID.randomUUID().toString))

    val kernel = (json \ "kernel").as[JsObject] +
      ("id" -> JsString(UUID.randomUUID().toString))

    // check if there's already a session for given path
    Ok(json + ("kernel" -> kernel))
  }

  // serve /api/config
  def config(app: String) = Action {
    val configValue = conf.getObject(s"$app")
    Ok(configValue match {
      case Some(cfg) => Json.parse(
        cfg.render(ConfigRenderOptions.concise().setJson(true)))
      case None => Json.obj()
    })
  }

  // serve files under custom
  def custom(file: String) = Action { request =>
    val fileToServe = new File("./resources", file)
    if (fileToServe.exists) {
      Ok.sendFile(fileToServe, inline = true)
    } else {
      NotFound
    }
  }

  // serve /notebooks url
  def notebook(path: String) = Action {
    val file = toFile(path)
    if (file.exists()) {
      Ok(views.html.notebook(Map(
        "base-url" -> baseUrl,
        "notebook-path" -> notebookHome
          .toPath
          .normalize()
          .relativize(file.toPath)
          .toString,
        "notebook-name" -> file.getName
      )))
    } else {
      NotFound
    }
  }

  // serve /tree url
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
    Ok(views.html.tree(breadcrumbs, Map(
      "base-url" -> baseUrl,
      "notebook-path" -> Option(path).getOrElse(""))))
  }

  def contents(path: String, contentType: String) = Action { request =>
    val file = toFile(path)
    if (!file.exists()) {
      NotFound
    } else if (file.isDirectory) {
      val homePath = notebookHome.toPath.normalize()
      val files = file.listFiles
        .filter(!_.getName.startsWith("."))
        .map { f =>
          val relativePath = homePath.relativize(f.toPath)
          val contentType = f.getName match {
            case name: String if name.endsWith(".ipynb") => "notebook"
            case _: String if f.isDirectory => "directory"
            case _ => "file"
          }
        Json.obj("type" -> contentType, "name" -> f.getName, "path" -> relativePath.toString)
      }
      Ok(Json.obj("content" -> files))
    } else {
      val includeContent = request.getQueryString("content") match {
        case Some("0") => false
        case _ => true
      }
      Ok(Contents.load(file, path, includeContent))
    }
  }

  def delete(path: String) = Action {
    val file = toFile(path)
    if (file.exists && file.isDirectory) {
      Try { scala.reflect.io.Path(file).deleteRecursively() }
    } else {
      file.delete()
    }
    NoContent
  }

  def save(path: String) = Action(parse.tolerantJson(maxLength)) { request =>
    val file = toFile(path)
    Contents.save(file, (request.body \ "content").as[JsObject])
    Ok(Contents.load(file, path))
  }

  private def toFile(path: String): File = {
    new File(notebookHome, Option(path)
        .map(p => URLDecoder.decode(p, "UTF-8"))
        .getOrElse("."))
  }
}
