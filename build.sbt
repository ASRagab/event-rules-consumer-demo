val ZIOVersion = "2.0.15"

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.11"

lazy val root = (project in file("."))
  .settings(
    name := "events-rules-consumer"
  )

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias(
  "check",
  "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck"
)

libraryDependencies ++= Seq(
  // ZIO
  "dev.zio"        %% "zio"               % ZIOVersion,
  "dev.zio"        %% "zio-streams"       % ZIOVersion,
  "dev.zio"        %% "zio-test"          % ZIOVersion,
  "dev.zio"        %% "zio-json"          % "0.5.0",
  "dev.zio"        %% "zio-connect-file"  % "0.4.4",
  "dev.zio"        %% "zio-kafka"         % "2.4.1",
  "dev.zio"        %% "zio-logging"       % "2.1.13",
  "dev.zio"        %% "zio-logging-slf4j" % "2.1.13",
  "dev.zio"        %% "zio-kafka-testkit" % "2.4.1" % "test",
  "dev.zio"        %% "zio-test"          % ZIOVersion % "test",
  "dev.zio"        %% "zio-test-sbt"      % ZIOVersion % "test",
  "ch.qos.logback" % "logback-classic"    % "1.4.7"
)

testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))

Compile / console / scalacOptions := Seq(
  "-Ymacro-annotations",
  "-Ypartial-unification",
  "-language:higherKinds",
  "-language:existentials",
  "-Yno-adapted-args",
  "-Xsource:2.13",
  "-Yrepl-class-based",
  "-deprecation",
  "-encoding",
  "UTF-8",
  "-explaintypes",
  "-Yrangepos",
  "-feature",
  "-Xfuture",
  "-unchecked",
  "-Xlint:_,-type-parameter-shadow",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-opt-warnings",
  "-Ywarn-extra-implicit",
  "-Ywarn-unused:_,imports",
  "-Ywarn-unused:imports",
  "-opt:l:inline",
  "-opt-inline-from:<source>",
  "-Ypartial-unification",
  "-Yno-adapted-args",
  "-Ywarn-inaccessible",
  "-Ywarn-infer-any",
  "-Ywarn-nullary-override",
  "-Ywarn-nullary-unit"
)
