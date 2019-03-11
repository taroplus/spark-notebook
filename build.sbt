import com.typesafe.sbt.packager.MappingsHelper._

name := "sparkling-notebook"
libraryDependencies += guice

lazy val commonSettings = Seq(
  organization := "taroplus",
  version := "0.5",
  scalaVersion := "2.12.4"
)

lazy val server = (project in file(".")).enablePlugins(PlayScala)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-repl" % "2.4.0",
      "org.apache.spark" %% "spark-streaming" % "2.4.0",
      "com.thoughtworks.paranamer" % "paranamer" % "2.6",
      "org.apache.hadoop" % "hadoop-client" % "2.7.2",
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.8.10",
      "com.typesafe.play" %% "play-json" % "2.6.8"
    )
  )

unmanagedClasspath in Runtime += file("/Users/konishio/workspace/spark-tools/target/classes")
mappings in Universal ++= directory(baseDirectory.value / "resources")
mappings in Universal ++= directory(baseDirectory.value / "notebooks")
mappings in Universal ++= directory(baseDirectory.value / "python")
