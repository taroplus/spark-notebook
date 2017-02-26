package taroplus.kernel
import play.api.libs.json.{JsObject, Json}

/**
  * Python Kernel - Bridge to Python process via Py4j
  */
class PythonKernel extends Kernel {
  override val info: JsObject = {
    Json.obj(
      "language_info" -> Json.obj(
        "name" -> "python",
        "codemirror_mode" -> Json.obj("version" -> 2, "name" -> "ipython")
      ))
  }

  override def execute(stream: StreamAppender, msg: JsObject, counter: Int): JsObject = {
    Json.obj("execution_count" -> counter)
  }
}
