
/** sbt imports **/
import sbt._
import Keys._
import com.typesafe.sbteclipse.plugin.EclipsePlugin._
import com.typesafe.sbt.SbtStartScript
import sbt.Project.Initialize

object PhillyBus extends Build {

  lazy val defaultSettings = Defaults.defaultSettings ++ Seq(
    organization := "hashtag",
    version := "0.0.1",
    scalaVersion := Dependency.V.Scala,
    EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.Unmanaged, EclipseCreateSrc.Source, EclipseCreateSrc.Resource),
    EclipseKeys.withSource := true
  )

  lazy val compileJdk7Settings = Seq(
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked", "-optimize", "-feature", "-language:postfixOps", "-target:jvm-1.7"),
    javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation", "-source", "1.7", "-target", "1.7")
  )

  lazy val root = Project(id ="phillybus",
                          base = file("."),
                          settings = defaultSettings ++ Unidoc.settings ++ SbtStartScript.startScriptForClassesSettings ++ Seq(
                            publishArtifact := false,
                            logLevel := Level.Warn,
                            libraryDependencies ++= Dependencies.phillybus))
}

object Dependencies {
  import Dependency._
  val phillybus = Seq(
    Dependency.akkaActor, Dependency.scalatime, Dependency.squeryl, Dependency.json4sjackson,  Dependency.socko, Dependency.scalaj,
    Dependency.scalamock, Dependency.csvReader, Dependency.h2
  )
}

object Dependency {
  object V {
    val Scala       = "2.10.3"
    val Akka        = "2.2.3"
  }

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % V.Akka
  val scalatime = "com.github.nscala-time" %% "nscala-time" % "0.8.0"
  val squeryl = "org.squeryl" %% "squeryl" % "0.9.5-6"
  val json4sjackson = "org.json4s" %% "json4s-jackson" % "3.2.6"
  val slf4j = "org.slf4j" % "slf4j-nop" % "1.6.4"
  val socko = "org.mashupbots.socko" %% "socko-webserver" % "0.4.1"
  val scalaj = "org.scalaj" %% "scalaj-http" % "0.3.14"
  val scalamock = "org.scalamock" %% "scalamock-scalatest-support" % "3.0.1" % "test"
  val csvReader= "com.github.tototoshi" %% "scala-csv" % "1.0.0"
  val h2 = "com.h2database" % "h2" % "1.3.166"
}
