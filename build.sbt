
name := "alpakka-jms-example"

organization := "io.github.sullis"

scalaVersion := "2.13.8"

crossScalaVersions := Seq(scalaVersion.value)

scalacOptions += "-target:jvm-1.8"

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

val logbackVersion = "1.2.11"

libraryDependencies ++= Seq(
  "org.gfccollective" %% "gfc-logging" % "1.0.0",
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "ch.qos.logback" % "logback-core" % logbackVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-jms" % "3.0.4",
  "io.github.sullis" %% "jms-testkit" % "1.0.4" % Test,
  "org.mockito"    % "mockito-core" % "4.4.0" % Test,
  "org.scalatest" %% "scalatest" % "3.2.11" % Test
)

updateOptions := updateOptions.value.withGigahorse(false)

publishMavenStyle := true

Test / publishArtifact := false

pomIncludeRepository := { _ => false }

licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

// ScoverageKeys.coverageMinimum := 1.0

// ScoverageKeys.coverageFailOnMinimum := true
