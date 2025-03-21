import Dependencies._

val zioHttpVersion = "3.1.0"
val circeVersion   = "0.14.11"

ThisBuild / scalaVersion     := "2.13.16"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

val neotypesVersion = "1.2.1"
lazy val root = (project in file("."))
  .settings(
    name                        := "rec-system-be",
    libraryDependencies += munit % Test,
    libraryDependencies ++= Seq(
      "dev.zio"         %% "zio-http"                    % zioHttpVersion,
      "dev.zio"         %% "zio-logging"                 % "2.5.0",
      "dev.zio"         %% "zio-interop-reactivestreams" % "2.0.2",
      "org.neo4j.driver" % "neo4j-java-driver"           % "5.28.4"
    ),
    libraryDependencies ++= Seq(
      "io.github.neotypes" %% "neotypes-zio",
      "io.github.neotypes" %% "neotypes-zio-stream",
      "io.github.neotypes" %% "neotypes-generic"
    ).map(_ % neotypesVersion),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % circeVersion)
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
