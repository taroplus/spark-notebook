package taroplus

import akka.actor.{Actor, Props}
import taroplus.kernel.{PythonKernel, ScalaKernel}

/**
  * Interpreter representation - Akka Actor
  */
class InterpreterActor extends Actor {
  private lazy val executor = context.actorOf(Props[SingleExecutor])

  private val scala = new ScalaKernel
  private val python = new PythonKernel

  override def postStop(): Unit = {
    scala.stop()
    python.stop()
  }

  override def preStart(): Unit = {
    scala.setup()
  }

  override def receive: Receive = {
    case _ =>
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
