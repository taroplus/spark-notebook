package taroplus.kernel
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import taroplus.spark.SparkSystem
import taroplus.utils.InterceptOutputStream

import scala.tools.nsc.interpreter.{Completion, IR}

/**
  * Scala Kernel
  */
class ScalaKernel extends Kernel {
  private val logger = LoggerFactory.getLogger(this.getClass)
  override val info: JsObject = {
    Json.obj(
      "language_info" -> Json.obj(
        "name" -> "scala",
        "codemirror_mode" -> "text/x-scala"
      ))
  }

  override def execute(stream: StreamAppender, msg: JsObject, counter: Int): JsObject = {
    val intp = SparkSystem.iloop.intp
    val original_code = (msg \ "content" \ "code").as[String]
    // normalized code
    val code = if (Completion.looksLikeInvocation(original_code) && intp.mostRecentVar != null) {
      intp.mostRecentVar + original_code
    } else {
      original_code
    }

    val result = Console.withOut(InterceptOutputStream.instance) {
      InterceptOutputStream.intercept(stream.append) {
        intp.interpret(code)
      }
    }

    result match {
      case IR.Incomplete => stream.append("SyntaxError: invalid syntax")
      case _ => ()
    }
    Json.obj("execution_count" -> counter, "status" -> "ok")
  }

  override def start(conf: Configuration): Unit = {
    // init script
    conf.getString("kernel.scala.initScript") match {
      case Some(script) if script.length > 0 =>
        logger.info("Running init script")
        SparkSystem.iloop.intp.beQuietDuring {
          SparkSystem.iloop.processLine(script)
        }
      case _ =>
    }
  }
}
