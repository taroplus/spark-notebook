package taroplus.kernel

import javax.inject.{Inject, Singleton}

import akka.actor.{ActorSystem, PoisonPill, Props}
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class KernelAccess @Inject()(lifecycle: ApplicationLifecycle)(
    implicit actorSystem: ActorSystem, ec: ExecutionContext){

  private val kernelActor = actorSystem.actorOf(Props(classOf[KernelActor]))

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
}
