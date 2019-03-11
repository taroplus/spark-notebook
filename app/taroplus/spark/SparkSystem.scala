package taroplus.spark

import java.io.File
import java.net.URLClassLoader

import akka.actor.ActorSystem
import org.apache.spark.SparkConf
import org.apache.spark.deploy.SparkSubmit
import org.apache.spark.repl.{Main, SparkILoop}
import org.slf4j.LoggerFactory
import taroplus.utils.InterceptOutputStream

import scala.collection.mutable.ArrayBuffer
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.JPrintWriter

/**
  * Manages Spark's lifecycle and its interpreter
  */
object SparkSystem {
  private val logger = LoggerFactory.getLogger(this.getClass)
  // disable ansi color
  sys.props.put("scala.color", "false")

  var iloop: SparkILoop = _
  var backupClassLoader: ClassLoader = _

  /**
    * SparkSubmit class calls here
    */
  def main(args: Array[String]): Unit = {
    val user_jars = getUserJars(Main.conf, isShell = true)
    val interpArguments = List(
      "-Yrepl-class-based",
      "-Yrepl-outdir", s"${Main.outputDir.getAbsolutePath}",
      "-classpath", (user_jars ++ getSystemJars).mkString(File.pathSeparator)
    )

    iloop.settings = new Settings()
    iloop.settings.processArguments(interpArguments, processAll = true)

    // create and initialize the interpreter
    iloop.createInterpreter()
    iloop.intp.initializeSynchronous()

    // this interactive reader is not used, but this is required
    // to handle processLine method
    iloop.in = iloop.chooseReader(iloop.settings)
    iloop.initializeSpark()
  }

  /**
    * Setting up IMain and make Spark variables available
    */
  def start(system: ActorSystem): Unit = {
    logger.info("Starting SparkSystem")
    iloop = new ProcessLineFixed()
    // back up current class loader
    backupClassLoader = Thread.currentThread().getContextClassLoader
    // use SparkSubmit to setup spark specific things, use an empty jar
    // as its target jar
    getSystemJars.find(_.contains("submit-target.jar")) match {
      case Some(path) => SparkSubmit.main(Array("--class", "taroplus.spark.SparkSystem", path))
      case _ =>
        logger.error("Unable to locate the dummy file: submit-target.jar")
    }
    Main.sparkContext.addSparkListener(EventListener.instance(system))
  }

  /**
    * Shut down the interpreter and close Spark variables
    */
  def stop(): Unit = {
    logger.info("Shutting down SparkSystem")
    Option(Main.sparkSession).foreach(_.stop())
    // null spark variables
    Main.sparkSession = null
    Main.sparkContext = null
    iloop.closeInterpreter()
    // restore current class loader
    Thread.currentThread().setContextClassLoader(backupClassLoader)
  }

  def interrupt(): Unit = {
    Main.sparkContext.cancelAllJobs()
  }

  // Play's dev mode doesn't have explicit classpath, so this is extracting
  // them from classloader tree
  private def getSystemJars: Seq[String] = {
    val buffer = new ArrayBuffer[String]
    var cl = Thread.currentThread().getContextClassLoader
    while(cl != null) {
      cl match {
        case urlLoader: URLClassLoader =>
          urlLoader.getURLs.foreach(u => {
            buffer.append(u.getPath)
          })
        case _ => ()
      }
      cl = cl.getParent
    }
    buffer
  }

  /**
    * Copied from org.apache.spark.util.Utils
    */
  private def getUserJars(conf: SparkConf, isShell: Boolean = false): Seq[String] = {
    val sparkJars = conf.getOption("spark.jars")
    if (conf.get("spark.master") == "yarn" && isShell) {
      val yarnJars = conf.getOption("spark.yarn.dist.jars")
      unionFileLists(sparkJars, yarnJars).toSeq
    } else {
      sparkJars.map(_.split(",")).map(_.filter(_.nonEmpty)).toSeq.flatten
    }
  }

  private def unionFileLists(leftList: Option[String], rightList: Option[String]): Set[String] = {
    var allFiles = Set[String]()
    leftList.foreach { value => allFiles ++= value.split(",") }
    rightList.foreach { value => allFiles ++= value.split(",") }
    allFiles.filter { _.nonEmpty }
  }

  // original implementation tries to access globalFuture which is null
  // in this use case, so overriding that function
  class ProcessLineFixed extends SparkILoop(None, new JPrintWriter(InterceptOutputStream.instance, true)) {
    override def processLine(line: String): Boolean = {
      command(line) match {
        case Result(false, _)      => false
        case Result(_, Some(line)) => addReplay(line) ; true
        case _                     => true
      }
    }
  }
}
