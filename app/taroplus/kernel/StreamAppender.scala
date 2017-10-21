package taroplus.kernel

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, Cancellable}
import play.api.libs.json.{JsValue, Json}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration

/**
  * This class manages output stream coming from code
  * @param ref ActorRef
  * @param msg JsValue
  * @param system ActorSystem
  */
class StreamAppender(ref: ActorRef, msg: JsValue, system: ActorSystem) {
  private val REGEX_STACKTRACE = "^.+Exception[^\\n]++\\n(\\s+at .++\\n)+".r
  private val REGEX_CONSOLE_LINE = "^\\s+at \\$iwC.+".r

  private var currentTimer: Cancellable = _
  private val buffer = ArrayBuffer[String]()

  // execution context
  private implicit val ec = system.dispatcher

  def flush(): Unit = {
    tryCancelTimer()
    val lines = buffer.synchronized {
      val lines = buffer.toList
      buffer.clear()
      lines
    }
    val content = lines.reverse
      .reverse
      .mkString

    ref ! reply_message(msg, "stream", "iopub",
      Json.obj("text" -> content, "name" -> "stdout"))
  }

  def append(output: String): Unit = {
    // for the first message, send immediately
    tryCancelTimer()
    buffer.synchronized {
      buffer.append(normalize(output))
    }
    // no wait for the first message
    if (currentTimer == null) {
      flush()
    }
    currentTimer = system.scheduler.scheduleOnce(Duration(200, TimeUnit.MILLISECONDS)) {
      flush()
    }
  }

  // strips internal stack from output
  private def normalize(text: String): String = {
    REGEX_STACKTRACE.replaceAllIn(text, m => {
      val lines = m.group(0).split("\n")
      val updated = lines.indexWhere(l => REGEX_CONSOLE_LINE.findFirstIn(l).isDefined) match {
        case -1 =>
          // no internal stacktrace
          m.group(0)
        case i: Int =>
          (lines.take(i) :+ s"\t... ${lines.length - i} more\n")
            .mkString("\n")
      }
      // replaced string is treated as regex
      updated.replace("$", "\\$")
    })
  }

  private def tryCancelTimer(): Unit = {
    if (currentTimer != null && !currentTimer.isCancelled) {
      currentTimer.cancel()
    }
  }
}
