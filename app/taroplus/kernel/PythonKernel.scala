package taroplus.kernel

/**
  * Bridge to Python process via Py4j
  */
class PythonKernel extends Kernel {
  override def stop(): Unit = {
    println("***** PYTHON STOP *****")
  }
}
