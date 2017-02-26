package taroplus.utils

import java.io.OutputStream
import java.nio.charset.StandardCharsets

import scala.util.DynamicVariable

object InterceptOutputStream {
  private val NO_OP: String => Unit = _ => ()
  private[utils] val interceptor = new DynamicVariable[String => Unit](NO_OP)

  def intercept[T](func: String => Unit)(thunk: => T): T = {
    interceptor.withValue(func){ thunk }
  }
}

class InterceptOutputStream extends OutputStream {
  import InterceptOutputStream._
  override def write(b: Array[Byte], off: Int, len: Int): Unit = {
    interceptor.value(
      new String(b, off, len, StandardCharsets.UTF_8))
  }

  // can't handle this
  override def write(b: Int): Unit = {}
}
