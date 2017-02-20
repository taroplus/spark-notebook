package taroplus.kernel
import play.api.libs.json.{JsObject, Json}

/**
  * Scala Kernel
  */
class ScalaKernel extends Kernel {
  override val info: JsObject = {
    Json.obj(
      "language_info" -> Json.obj(
        "name" -> "scala",
        "codemirror_mode" -> "text/x-scala"
      ))
  }

  override def stop(): Unit = {
  }
}
