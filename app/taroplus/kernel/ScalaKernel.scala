package taroplus.kernel

/**
  * SparkSession lives here
  */
class ScalaKernel extends Kernel {
  override def stop(): Unit = {
    println("===== STOP =====")
  }

  def setup(): Unit = {
    println("++++++ SETUP ++++++")
  }
}
