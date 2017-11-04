import com.typesafe.sbt.packager.MappingsHelper._

name := "sparkling-notebook"

lazy val commonSettings = Seq(
  organization := "taroplus",
  version := "0.1",
  scalaVersion := "2.11.8"
)

lazy val server = (project in file(".")).enablePlugins(PlayScala)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-repl" % "2.2.0",
      "org.apache.spark" %% "spark-streaming" % "2.2.0",
      "com.thoughtworks.paranamer" % "paranamer" % "2.6",
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.7.8"
    )
  )

unmanagedClasspath in Runtime += file("/Users/konishio/workspace/spark-tools/target/classes")
mappings in Universal ++= directory(baseDirectory.value / "resources")