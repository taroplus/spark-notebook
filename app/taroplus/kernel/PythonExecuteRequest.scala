package taroplus.kernel

import java.util.concurrent.atomic.AtomicBoolean

class PythonExecuteRequest(_code: String, stream: StreamAppender) {
  private val resultPromise = new Object()
  private val completed: AtomicBoolean = new AtomicBoolean(false)

  def code(): String = _code

  // release the object
  def complete(): Unit = {
    resultPromise.synchronized {
      completed.set(true)
      resultPromise.notify()
    }
  }

  // forward
  def svg(svg: String): Unit = stream.svg(svg)

  def write(message: String): Unit = {
    if (message != null && message.nonEmpty) {
      stream.append(message)
    }
  }

  // Blocking call to receive result
  private[kernel] def waitForCompletion(timeout: Long): Boolean = {
    if (completed.get()) {
      true
    } else {
      resultPromise.synchronized {
        resultPromise.wait(timeout)
      }
      completed.get()
    }
  }
}
