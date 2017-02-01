name := "spark-notebook"

version := "0.1-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

libraryDependencies += "org.apache.spark" %% "spark-repl" % "2.1.0"
