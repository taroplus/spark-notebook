package taroplus.kernel

import java.util.concurrent.ConcurrentLinkedQueue

import akka.actor.{Actor, ActorRef, Props}
import org.slf4j.LoggerFactory
import play.api.Configuration
import play.api.libs.json.{JsObject, JsValue, Json}
import taroplus.spark.{EventListener, SparkSystem}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * Interpreter representation - Akka Actor
  */
class KernelActor(conf: Configuration) extends Actor {
  private val logger = LoggerFactory.getLogger(this.getClass)
  private lazy val executor = context.actorOf(Props(new SingleExecutor))

  // the task queue
  private val tasks = new ConcurrentLinkedQueue[(ActorRef, JsObject)]()

  private val scala = new ScalaKernel
  private val python = new PythonKernel

  // execution counter
  private var counter = 1

  override def postStop(): Unit = {
    logger.info("Stopping KernelActor")
    tasks.clear()
    scala.stop()
    python.stop()
    SparkSystem.stop()
  }

  override def preStart(): Unit = {
    logger.info("Starting KernelActor")
    SparkSystem.start(context.system)
    scala.start(conf)
    python.start(conf)
  }

  override def receive: Receive = {
    case msg: JsObject =>
      val header = (msg \ "header").as[JsValue]
      logger.info("MSG: {}", header \ "msg_type")

      (header \ "msg_type").as[String] match {
        case "reset" =>
          scala.stop()
          python.stop()
          SparkSystem.stop()
          SparkSystem.start(context.system)
          scala.start(conf)
          python.start(conf)

        case "interrupt" =>
          logger.info("Cancel All jobs and tasks")
          tasks.clear()
          SparkSystem.interrupt()

        case "execute_request" =>
          tasks.add((sender, msg))
          executor ! POLL

        case "kernel_info_request" =>
          sender ! reply_message(msg, "kernel_info_reply", "shell", getKernel(msg).info)

        case _ =>
          logger.warn("Unknown message {}", msg)
      }
  }

  private def getKernel(msg: JsValue): Kernel = {
    (msg \ "kernel_name").as[String] match {
      case "scala" => scala
      case "python" => python
    }
  }

  class SingleExecutor extends Actor {
    var isRunning: Boolean = _
    var ref: ActorRef = _
    var msg: JsObject = _

    override def preStart(): Unit = {
      implicit val executor: ExecutionContext = context.dispatcher
      // this timer is to prevent timeout while running
      context.system.scheduler.schedule(
        Duration.Zero,
        Duration(10, SECONDS),
        () => {
          if (isRunning && ref != null && msg != null)
            ref ! Json.obj("channel" -> "iopub",
              "msg_type" -> "ping",
              "header" -> Json.obj("msg_type" -> "ping"))
        })
    }

    def receive: Receive = {
      case POLL if !isRunning =>
        synchronized {
          if (!isRunning && !tasks.isEmpty) {
            isRunning = true

            // grab a task to run
            val task = tasks.poll()
            ref = task._1
            msg = task._2
            EventListener.current(ref, msg)

            // status busy
            ref ! reply_message(msg, "status", "iopub", Json.obj("execution_state" -> "busy"))

            val appender = new StreamAppender(ref, msg, context.system)
            // run the code
            val reply_content = getKernel(msg).execute(appender, msg, counter)
            appender.flush()

            // reset spark progress bar
            EventListener.reset()

            // execute reply
            ref ! reply_message(msg, "execute_reply", "shell", reply_content)

            // status idle
            ref ! reply_message(msg, "status", "iopub", Json.obj("execution_state" -> "idle"))

            // status idle
            counter = counter + 1
            isRunning = false
            self ! POLL
          }
        }
      case _ => ()
    }
  }

  // Message to wake up the single executor
  case object POLL
}
