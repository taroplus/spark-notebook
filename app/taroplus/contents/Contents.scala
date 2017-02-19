package taroplus.contents

import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes

import play.api.libs.json.{JsArray, JsObject, JsString, Json}

/**
  * Manages File IO for notebook / text
  */
object Contents {
  // load content from path
  def load(file: File, orgPath: String, includeContent: Boolean = false): JsObject = {
    val attr = Files.readAttributes(file.toPath, classOf[BasicFileAttributes])
    val tpe = if (file.getName.endsWith(".ipynb")) "notebook" else "file"
    val json = Json.obj(
      "type" -> tpe,
      "name" -> file.getName,
      "last_modified" -> attr.lastModifiedTime().toString,
      "created" -> attr.creationTime().toString,
      "path" -> URLDecoder.decode(orgPath, "UTF-8"),
      "writable" -> file.canWrite,
      "format" -> "json"
    )

    if (includeContent) {
      val fileBody = new String(Files.readAllBytes(file.toPath), StandardCharsets.UTF_8)
      val original = Json.parse(fileBody).as[JsObject]
      val cells = (original \ "cells").as[JsArray]
        .value
        .map(v => joinMultiLines(v.as[JsObject]))
      json + ("content" -> (original + ("cells" -> JsArray(cells))))
    } else {
      json
    }
  }

  // save content to disk
  def save(file: File, content: JsObject): Unit = {
    // needs to split source / output
    if (file.getName.endsWith(".ipynb")) {
      // need to split multi lines
      val cells = (content \ "cells").as[JsArray]
        .value
        .map(v => splitMultiLines(v.as[JsObject]))
      // final json to save
      val jsonToSave = content + ("cells" -> JsArray(cells))

      Files.write(
        file.toPath,
        Json.stringify(jsonToSave).getBytes(StandardCharsets.UTF_8))

    } else {
      // todo
    }
  }

  // split a string in 'source' and 'outputs'
  private def splitMultiLines(cell: JsObject): JsObject  = {
    val source = split((cell \ "source").as[String])
    (cell \ "outputs").asOpt[JsArray] match {
      case Some(arr) =>
        val outputs = arr.value.map(v => splitOutputLines(v.as[JsObject]))
        cell + ("source" -> source) + ("outputs" -> JsArray(outputs))
      case None =>
        cell + ("source" -> source)
    }
  }

  // split a string in 'source' and 'outputs'
  private def splitOutputLines(output: JsObject): JsObject = {
    (output \ "output_type").as[String] match {
      // split text
      case "stream" => output + ("text" -> split((output \ "text").as[String]))
      case "execute_result" =>
        val data = (output \ "data").as[JsObject]
        (data \ "text/plain").asOpt[String] match {
          case Some(str) => output + ("data" -> (data + ("text/plain" -> split(str))))
          case _ => output
        }
      case _ =>
        output
    }
  }

  // combine lines in 'source' and 'outputs'
  private def joinMultiLines(cell: JsObject): JsObject = {
    val source = join((cell \ "source").as[JsArray])
    (cell \ "outputs").asOpt[JsArray] match {
      case Some(arr) =>
        val outputs = arr.value.map(v => joinOutputLines(v.as[JsObject]))
        cell + ("source" -> source) + ("outputs" -> JsArray(outputs))
      case None =>
        cell + ("source" -> source)
    }
  }

  // combine lines in 'stream' and 'execute_result/text/plain'
  private def joinOutputLines(output: JsObject): JsObject = {
    (output \ "output_type").as[String] match {
      case "stream" => output + ("text" -> join((output \ "text").as[JsArray]))
      case "execute_result" =>
        val data = (output \ "data").as[JsObject]
        (data \ "text/plain").asOpt[JsArray] match {
          case Some(arr) => output + ("data" -> (data + ("text/plain" -> join(arr))))
          case _ => output
        }
      case _ =>
        output
    }
  }

  // join string array into a single array
  private def join(arr: JsArray): JsString = JsString(arr.value.map(v => v.as[String]).mkString)
  // split single string into jsarray by splitting \n char
  private def split(str: String): JsArray = JsArray(str.split("(?<=\n)").map(JsString))
}
