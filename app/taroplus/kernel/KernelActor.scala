package taroplus.kernel

import java.util.UUID

import akka.actor.{Actor, Props}
import play.api.libs.json.{JsValue, Json}

/**
  * Interpreter representation - Akka Actor
  */
class KernelActor extends Actor {
  private lazy val executor = context.actorOf(Props[SingleExecutor])

  private val scala = new ScalaKernel
  private val python = new PythonKernel

  override def postStop(): Unit = {
    scala.stop()
    python.stop()
  }

  override def preStart(): Unit = {
  }

  override def receive: Receive = {
    case msg: JsValue =>
      val header = (msg \ "header").as[JsValue]

      (header \ "msg_type").as[String] match {
        case "kernel_info_request" =>
          val content = (msg \ "kernel_name").as[String] match {
            case "scala" => Json.obj(
              "language_info" -> Json.obj(
                "name" -> "scala",
                "codemirror_mode" -> "text/x-scala"
              ))
            case "python" => Json.obj(
              "language_info" -> Json.obj(
                "name" -> "python",
                "codemirror_mode" -> Json.obj("version" -> 2, "name" -> "ipython")
              ))
          }
          sender ! reply_message(header, "kernel_info_reply", "shell", content)
        case _ =>
          println(msg)
      }
  }

  private def reply_message(parent_header: JsValue,
      msgType: String,
      channel: String,
      content: JsValue): JsValue = {

    val msg_id = UUID.randomUUID().toString
    Json.obj("parent_header" -> parent_header,
      "msg_type" -> msgType,
      "msg_id" -> msg_id,
      "content" -> content,
      "header" -> Json.obj(
        "version" -> "5.0",
        "msg_type" -> msgType,
        "msg_id" -> msg_id
      ),
      "channel" -> channel
    )
  }

  class SingleExecutor extends Actor {
    var isRunning: Boolean = _
    def receive: Receive = {
      case POLL if !isRunning =>
        synchronized {
          isRunning = true
          // do something
          isRunning = false
          self ! POLL
        }
      case _ => ()
    }
  }

  // Message to wake up the single executor
  case object POLL
}
