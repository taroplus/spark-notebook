package taroplus.kernel

import play.api.libs.json.JsObject

trait Kernel {
  val info: JsObject
  def stop(): Unit
}
