
/** sbt imports **/
import sbt._
import Keys._
import com.typesafe.sbteclipse.plugin.EclipsePlugin._
import sbt.Project.Initialize

object PhillyBus extends Build {

  lazy val defaultSettings = Defaults.defaultSettings ++ Seq(
    organization := "hashtag",
    version := "0.0.1",
    scalaVersion := Dependency.V.Scala,
    EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.Unmanaged, EclipseCreateSrc.Source, EclipseCreateSrc.Resource),
    EclipseKeys.withSource := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
        if (isSnapshot.value)
          Some("snapshots" at nexus + "content/repositories/snapshots")
        else
          Some("releases" at nexus + "service/local/staging/deploy/maven2")
    }
  )

  lazy val compileJdk7Settings = Seq(
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked", "-optimize", "-feature", "-language:postfixOps", "-target:jvm-1.7"),
    javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation", "-source", "1.7", "-target", "1.7")
  )

  lazy val root = Project(id ="phillybus",
                          base = file("."),
                          settings = defaultSettings ++ Unidoc.settings ++ Seq(
                            publishArtifact := false,
                            scalacOptions in (Compile, doc) ++= Seq("-doc-root-content", baseDirectory.value+"/root-doc.html"),
                            logLevel := Level.Warn,
                            libraryDependencies ++= Dependencies.phillybus))
}

object Dependencies {
  import Dependency._
  val phillybus = Seq(
    Dependency.akkaActor, Dependency.scalatime, Dependency.squeryl, Dependency.json4sjackson, Dependency.slf4j, Dependency.socko, Dependency.scalaj,
    Dependency.scalamock
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
}
