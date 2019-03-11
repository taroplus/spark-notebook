package taroplus.spark

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem, Cancellable}
import org.apache.spark.Success
import org.apache.spark.scheduler._
import play.api.libs.json.{JsObject, Json}
import taroplus.kernel.reply_message

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

object EventListener {
  private var ref: ActorRef = _
  private var msg: JsObject = _
  private var id: String = _
  private var instance: EventListener = _

  def instance(system: ActorSystem): EventListener = {
    if (instance == null) {
      instance = new EventListener(system)
    }
    instance
  }

  def current(ref: ActorRef, msg: JsObject): Unit = {
    this.ref = ref
    this.msg = msg
  }

  def open(): Unit = {
    this.id = UUID.randomUUID().toString
    message("comm_open")
  }

  private def flush(): Unit = {
    message("comm_msg")
  }

  def reset(): Unit = {
    instance.tryCancelTimer()

    if (id != null) {
      // close the comm only when it's open
      message("comm_close")
      id = null
      instance.stages.clear()
    }
  }

  private def message(tpe: String): Unit = {
    val status = instance.status
    ref ! reply_message(msg, tpe, "iopub",
      Json.obj("comm_id" -> id,
        "data" -> Json.obj("scheduled" -> status._1, "running" -> status._2, "completed" -> status._3),
        "target_name" -> "spark.progress"))
  }
}

class EventListener(system: ActorSystem) extends SparkListener {
  import EventListener._

  private var currentTimer: Cancellable = _
  private val stages = mutable.Map[Int, StageTracker]()

  // execution context
  private implicit val ec: ExecutionContext = system.dispatcher

  override def onJobStart(jobStart: SparkListenerJobStart): Unit = {
    val firstJob = stages.isEmpty
    stages ++= jobStart.stageInfos.map(info => info.stageId -> new StageTracker(info))
    if (firstJob) {
      open()
    } else {
      onUpdate()
    }
  }

  override def onJobEnd(jobEnd: SparkListenerJobEnd): Unit = onUpdate()
  override def onStageCompleted(stageCompleted: SparkListenerStageCompleted): Unit = onUpdate()

  override def onTaskStart(taskStart: SparkListenerTaskStart): Unit = {
    stages.get(taskStart.stageId) match {
      case Some(stage) => stage.taskStart(taskStart)
      case _ => // unknown
    }
    onUpdate()
  }

  override def onTaskEnd(taskEnd: SparkListenerTaskEnd): Unit = {
    stages.get(taskEnd.stageId) match {
      case Some(stage) => stage.taskEnd(taskEnd)
      case _ => // unknown
    }
    onUpdate()
  }

  private def onUpdate(): Unit = {
    tryCancelTimer()
    // no wait for the first message
    if (currentTimer == null) {
      flush()
    }
    // schedule next flush
    currentTimer = system.scheduler.scheduleOnce(Duration(200, TimeUnit.MILLISECONDS)) {
      flush()
    }
  }

  private def status: (Int, Int, Int) = stages.values.map(_.state)
    .reduce((s1, s2) => (s1._1 + s2._1, s1._2 + s2._2, s1._3 + s2._3))

  private def tryCancelTimer(): Unit = {
    if (currentTimer != null && !currentTimer.isCancelled) {
      currentTimer.cancel()
    }
  }

  class StageTracker(info: StageInfo) {
    private val tasks = Array.fill(info.numTasks)(0)
    def state: (Int, Int, Int) = (tasks.count(_ == 0), tasks.count(_ == 1), tasks.count(_ == 2))

    def taskStart(taskStart: SparkListenerTaskStart): Unit = {
      tasks(taskStart.taskInfo.index) = 1
    }

    def taskEnd(taskEnd: SparkListenerTaskEnd): Unit = {
      taskEnd.reason match {
        case Success => tasks(taskEnd.taskInfo.index) = 2
        case _ => // ignore anything else
      }
    }
  }
}
