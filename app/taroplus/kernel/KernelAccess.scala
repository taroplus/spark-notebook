package taroplus.kernel

import java.util.UUID
import javax.inject.{Inject, Singleton}

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.{JsObject, JsString, JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class KernelAccess @Inject()(lifecycle: ApplicationLifecycle, conf: Configuration)(
    implicit actorSystem: ActorSystem, ec: ExecutionContext){

  private val kernelActor = actorSystem.actorOf(Props(new KernelActor(conf)))

  // kill the actor before this getting shut down
  lifecycle.addStopHook(() => Future{
    kernelActor ! PoisonPill
  })

  final val kernelSpecs: JsValue = Json.obj(
    "default" -> "scala",
    "kernelspecs" -> Json.obj(
      "scala" -> Json.obj(
        "name" -> "scala",
        "spec" -> Json.obj(
          "language" -> "scala",
          "display_name" -> "Spark"
        ),
        "resources" -> Json.obj()),
      "python" -> Json.obj(
        "name" -> "python",
        "spec" -> Json.obj(
          "language" -> "python",
          "display_name" -> "PySpark"
        ),
        "resources" -> Json.obj())
    )
  )

  private final val MSG_INTERRUPT = "interrupt"
  private final val MSG_RESET = "reset"

  // create kernel based on config, here it doesn't
  // create anything, just generate id and return the
  // kernel json
  def create(config: JsObject): JsObject = {
    val name = (config \ "name").as[String]
    config + ("id" -> JsString(generateId(name)))
  }

  def interrupt(): Unit = {
    kernelActor ! Json.obj("header" -> Json.obj("msg_type" -> MSG_INTERRUPT))
  }

  def restart(currentId: String): JsObject = {
    kernelActor ! Json.obj("header" -> Json.obj("msg_type" -> MSG_RESET))
    val name = resolveName(currentId)
    Json.obj("id" -> generateId(name), "name" -> name)
  }

  def send(id: String, message: JsObject)(implicit sender: ActorRef): Unit = {
    kernelActor ! (message + ("kernel_name" -> JsString(resolveName(id))))
  }

  // resolve kernel name from id, using its prefix
  private def resolveName(id: String): String = {
    id.substring(0, 2) match {
      case "s-" => "scala"
      case "p-" => "python"
    }
  }

  // generate 'fake' kernel id based on name, it puts
  // a prefix depending on kernel
  private def generateId(name: String): String = {
    val prefix = name match {
      case "scala" => "s-"
      case "python" => "p-"
    }
    prefix + UUID.randomUUID()
  }
}
