package taroplus.kernel

import play.api.libs.json.JsObject

trait Kernel {
  val info: JsObject
  def execute(stream: StreamAppender, msg: JsObject, counter: Int): JsObject
  def stop(): Unit = {}
}
