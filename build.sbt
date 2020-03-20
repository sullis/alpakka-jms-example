enablePlugins(Sonatype)

import scoverage.ScoverageKeys

name := "alpakka-jms-example"

organization := "io.github.sullis"

scalaVersion := "2.13.1"

crossScalaVersions := Seq(scalaVersion.value)

scalacOptions += "-target:jvm-1.8"

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

val logbackVersion = "1.2.3"

libraryDependencies ++= Seq(
  "org.gfccollective" %% "gfc-logging" % "1.0.0",
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "ch.qos.logback" % "logback-core" % logbackVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-jms" % "2.0.0-RC1",
  "io.github.sullis" %% "jms-testkit" % "0.2.8" % Test,
  "org.mockito"    % "mockito-core" % "3.3.3" % Test,
  "org.scalatest" %% "scalatest" % "3.1.1" % Test
)

updateOptions := updateOptions.value.withGigahorse(false)

useGpg := true

releaseCrossBuild := true

releasePublishArtifactsAction := PgpKeys.publishSigned.value

publishMavenStyle := true

publishTo := sonatypePublishTo.value

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

ScoverageKeys.coverageMinimum := 1.0

ScoverageKeys.coverageFailOnMinimum := true
