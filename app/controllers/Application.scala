package controllers

import java.io.File
import java.net.URLDecoder
import java.time.Instant
import java.util.UUID
import javax.inject.{Inject, Singleton}

import akka.actor.{Actor, ActorSystem, Props}
import akka.stream.Materializer
import com.typesafe.config.ConfigRenderOptions
import org.apache.commons.io.FileUtils
import play.Environment
import play.api.Configuration
import play.api.libs.json._
import play.api.libs.streams.ActorFlow
import play.api.mvc.{Action, Controller, WebSocket}
import taroplus.contents.Contents
import taroplus.kernel.KernelAccess

import scala.concurrent.ExecutionContext
import scala.util.Try

/**
 * main application controller
 */
@Singleton
class Application @Inject()(
    conf: Configuration,
    env: Environment,
    kernel: KernelAccess)(implicit actorSystem: ActorSystem,
    mat: Materializer,
    ec: ExecutionContext) extends Controller {

  // resource folder
  private final val resourcePath = new File(env.rootPath(), "resources")

  private final val baseUrl = "/"

  // default notebook home = notebook folder
  private final val notebookHome = conf.getString("notebook.home")
      .map(new File(_))
      .getOrElse(new File(env.rootPath(), "notebooks"))

  // max notebook size
  private final val maxLength = 10 * 1024 * 1024 // 10M

  // serve /api/kernelspecs
  def kernelspecs() = Action { Ok(kernel.kernelSpecs) }

  // this shares the single kernel, so there's
  // no such thing as session
  def getSessions = Action { Ok(JsArray()) }

  // new session
  def newSession = Action(parse.tolerantJson) { request =>
    val json = request.body.as[JsObject] + ("id" -> JsString(UUID.randomUUID().toString))
    Ok(json + ("kernel" -> kernel.create((json \ "kernel").as[JsObject])))
  }

  // send interrupt signal
  def interrupt(kernelId: String) = Action {
    kernel.interrupt()
    NoContent
  }

  // send interrupt signal
  def restart(kernelId: String) = Action {
    Ok(kernel.restart(kernelId))
  }

  // WebSocket connection
  def connect(kernelId: String, sessionId: String) = WebSocket.accept[JsValue, JsValue] { _ =>
    ActorFlow.actorRef(out => Props(
      new Actor {
        def receive: Receive = { case msg: JsObject => kernel.send(kernelId, msg)(out) }
      }))
  }

  // serve /api/config
  def config(app: String) = Action {
    val configValue = conf.getObject(s"config.$app")
    Ok(configValue match {
      case Some(cfg) => Json.parse(
        cfg.render(ConfigRenderOptions.concise().setJson(true)))
      case None => Json.obj()
    })
  }

  // serve files under custom
  def custom(file: String) = Action { request =>
    val fileToServe = new File(resourcePath, file)
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

  // serve '/tree' url
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

  def checkpoints(path: String) = Action { request =>
    val currentPath = toFile(path)
    if (currentPath.exists()) {
      request.method match {
        case "POST" =>
          Ok(Json.obj(
            "last_modified" -> Instant.ofEpochMilli(currentPath.lastModified).toString,
            "id" -> "checkpoint"))
        case _ =>
          Ok(Json.arr(
            Json.obj(
              "last_modified" -> Instant.ofEpochMilli(currentPath.lastModified).toString,
              "id" -> "checkpoint")
          ))
      }
    } else {
      NotFound
    }
  }

  def create(path: String) = Action(parse.tolerantJson) { request =>
    val baseDir = toFile(path)

    // type may be given, otherwise consider this is for a notebook
    val contentType = (request.body \ "type").asOpt[String]
      .getOrElse("notebook")

    // base file name
    val templateName = contentType match {
      case "directory" => "Untitled Folder%s"
      case "notebook" => "Untitled%s.ipynb"
      case _ => "Untitled%s"
    }

    // find a name for new content
    val targetPath = Stream.from(1).map {
        case 1 => templateName.format("")
        case i => templateName.format(" " + i.toString)
      }
      .map(new File(baseDir, _))
      .find(!_.exists())
      .head

    val relativePath = notebookHome.toURI.relativize(targetPath.toURI).getPath
    contentType match {
      case "directory" =>
        targetPath.mkdir()
        Created(Json.obj("type" -> contentType, "name" -> targetPath.getName, "path" -> relativePath))
      case "notebook" =>
        (request.body \ "copy_from").asOpt[String] match {
          case Some(s) =>
            val copyFrom = new File(notebookHome, s)
            FileUtils.copyFile(copyFrom, targetPath)
          case None =>
            // it has to set kernel
            val content = Json.prettyPrint(
              Json.obj(
                "nbformat_minor" -> 0,
                "nbformat" -> 4,
                "cells" -> JsArray(),
                "metadata" -> Json.obj()))
            FileUtils.writeStringToFile(targetPath, content)
        }
        Redirect(routes.Application.contents(relativePath))
      case _ =>
        FileUtils.writeStringToFile(targetPath, "")
        Redirect(routes.Application.contents(relativePath))
    }
  }

  def rename(path: String) = Action(parse.tolerantJson) { request =>
    val currentPath = toFile(path)
    if (currentPath.exists()) {
      val newPath = (request.body \ "path").as[String]
      val newFile = toFile(newPath)
      if (newFile.exists()) {
        Conflict
      } else {
        val includeContent = request.getQueryString("content") match {
          case Some("0") => false
          case _ => !currentPath.isDirectory
        }
        currentPath.renameTo(newFile)
        Ok(Contents.load(newFile, newPath, includeContent))
      }
    } else {
      NotFound
    }
  }

  private def toFile(path: String): File = {
    new File(notebookHome, Option(path)
        .map(p => URLDecoder.decode(p, "UTF-8"))
        .getOrElse("."))
  }
}
