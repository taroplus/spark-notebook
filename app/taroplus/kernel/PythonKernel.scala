package taroplus.kernel
import java.io.File
import java.util.concurrent.TimeUnit

import org.apache.spark.SparkContext
import org.apache.spark.api.java.JavaSparkContext
import org.apache.spark.repl.Main
import org.apache.spark.sql.SparkSession
import org.slf4j.LoggerFactory
import play.api.libs.json.{JsObject, Json}
import py4j.GatewayServer

/**
  * Python Kernel - Bridge to Python process via Py4j
  */
class PythonKernel extends Kernel {
  private val logger = LoggerFactory.getLogger(this.getClass)
  private var process: Process = _

  private var gatewayServer: GatewayServer = _
  private var pendingRequest: PythonExecuteRequest = _

  def sparkContext(): JavaSparkContext = {
    JavaSparkContext.fromSparkContext(Main.sparkContext)
  }

  def sparkSession(): SparkSession = {
    Main.sparkSession
  }

  // returns next execute request
  def nextExecuteRequest(): PythonExecuteRequest = {
    pendingRequest
  }

  override val info: JsObject = {
    Json.obj(
      "language_info" -> Json.obj(
        "name" -> "python",
        "codemirror_mode" -> Json.obj("version" -> 2, "name" -> "ipython")
      ))
  }

  override def execute(stream: StreamAppender, msg: JsObject, counter: Int): JsObject = {
    // make sure background python is running
    ensureProcess()

    val code = (msg \ "content" \ "code").as[String]
    // create a request for execution and enqueue
    pendingRequest = new PythonExecuteRequest(code, stream)
    // wait for results
    while(!pendingRequest.waitForCompletion(1000) && process.isAlive) {
      //
    }
    pendingRequest = null

    Json.obj("execution_count" -> counter, "status" -> "ok")
  }

  override def start(): Unit = {
    gatewayServer = new GatewayServer(this, 0)
    gatewayServer.start()
    logger.info("Starting py4j gateway server at {}", gatewayServer.getListeningPort)
  }

  override def stop(): Unit = {
    gatewayServer.shutdown()
    if (process != null) {
      process.destroy()
    }
    if (pendingRequest != null) {
      pendingRequest.complete()
    }
  }

  private def ensureProcess(): Unit = {
    if (process == null || !process.isAlive) {
      val builder = new ProcessBuilder(
        sys.env.getOrElse("PYSPARK_PYTHON", "python"),
        "./python/main.py",
        gatewayServer.getListeningPort.toString)

      val spark_home = Main.conf
        .getOption("spark.home")
        .orElse(Option(System.getenv("PYSPARK_HOME")))
        .orElse(Option(System.getenv("SPARK_HOME")))
        .getOrElse(".")
      logger.info("Launching python process with spark_home = {}", spark_home)

      // look for necessary libraries
      val lib = new File(spark_home, "python/lib")
      if (!lib.exists() || lib.list() == null) {
        logger.error("pyspark lib directory does not exist: {}", lib)
      }

      val python_libs = lib.listFiles()
        .filter(s => s.getName.endsWith(".zip"))
        .map(_.getAbsolutePath)

      val env = builder.environment()
      env.put("PYTHONPATH", (sys.env.get("PYTHONPATH").toSeq ++ python_libs).mkString(File.pathSeparator))
      env.put("SPARK_HOME", spark_home)

      builder.inheritIO()
      process = builder.start()
    }
  }
}
