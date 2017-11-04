package taroplus.kernel

import play.api.Configuration
import play.api.libs.json.JsObject

/**
  * Kernel definition trait
  */
trait Kernel {
  // returns kernel info
  val info: JsObject

  // prepare for execution
  def start(conf: Configuration): Unit

  // execute given code
  def execute(stream: StreamAppender, msg: JsObject, counter: Int): JsObject

  // stop the kernel
  def stop(): Unit = {}
}
