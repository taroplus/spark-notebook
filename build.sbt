import com.typesafe.sbt.packager.MappingsHelper._

name := "spark-notebook"

lazy val commonSettings = Seq(
  organization := "taroplus",
  version := "0.1-SNAPSHOT",
  scalaVersion := "2.11.8"
)

lazy val server = (project in file(".")).enablePlugins(PlayScala)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-repl" % "2.1.0"
    )
  )

mappings in Universal ++= directory(baseDirectory.value / "resources")