name := """welcomer-framework"""

version := "0.1.1"

scalaVersion := "2.10.4"

scalacOptions := Seq("-feature", "-unchecked", "-deprecation", "-encoding", "utf8")

fork in (Test) := false

EclipseKeys.withSource := true

val akkaVersion = "2.3.6"
val sprayVersion = "1.3.2"

// TODO: Look into adding this? https://github.com/spray/sbt-revolver

libraryDependencies ++= Seq(
  "com.typesafe.akka"   %%  "akka-actor"          % akkaVersion,
  //"com.typesafe.akka"   %%  "akka-remote"         % akkaVersion,
  "com.typesafe.akka"   %%  "akka-slf4j"          % akkaVersion,
  "com.typesafe.akka"   %%  "akka-testkit"        % akkaVersion % "test",
  "io.spray"            %%  "spray-client"        % sprayVersion,
  "io.spray"            %%  "spray-can"           % sprayVersion,
  "io.spray"            %%  "spray-routing"       % sprayVersion,
  "io.spray"            %%  "spray-testkit"       % sprayVersion  % "test",
  //"org.reactivemongo"   %%  "reactivemongo"       % "0.10.5.akka23-SNAPSHOT",
  "org.reactivemongo"   %%  "play2-reactivemongo" % "0.10.5.0.akka23", // Gives us JsValue support instead of BSON
  "com.typesafe.play"   %%  "play-json"           % "2.3.4",
  "ch.qos.logback"      %   "logback-classic"     % "1.1.2",
  "com.casualmiracles"  %%  "treelog"             % "1.2.2",
  "org.scalaz"          %%  "scalaz-core"         % "7.0.6",
  //"org.scalaz"          %% "scalaz-core"    % "7.1.0",
  //"kamon"               %%  "kamon-spray"   % "0.3.5",
  "org.apache.commons"  %   "commons-lang3"       % "3.3.2",
  "org.scalatest"       %% "scalatest"      % "2.1.6" % "test" // Included by scalamock
  //"org.scalamock"       %% "scalamock-scalatest-support" % "3.1.RC1" % "test", // 3.0.1
)

resolvers ++= Seq(
  "Typesafe Maven Repository" at "http://repo.typesafe.com/typesafe/maven-releases/",
  //"spray repo" at "http://repo.spray.io",
  //"Kamon Repository" at "http://repo.kamon.io",
  "Sonatype OSS Releases"  at "http://oss.sonatype.org/content/repositories/releases/"
  //"Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
)
