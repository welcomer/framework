name := "welcomer-framework"

lazy val akkaVersion = settingKey[String]("The version of Akka used for building.")

lazy val sprayVersion = settingKey[String]("The version of Spray used for building.")

lazy val playJsonVersion = settingKey[String]("The version of Play Json used for building.")

// https://github.com/softprops/bintray-sbt (publish / bintrayUnpublish)
lazy val bintrayPublishSettings = Seq(
  bintrayOrganization := Some("welcomer"),
  licenses += ("Apache-2.0", url("http://choosealicense.com/licenses/apache-2.0/")),
  bintrayReleaseOnPublish in ThisBuild := true
)

lazy val commonSettings = Seq(
  version := "0.1.5-SNAPSHOT",
  scalaVersion := "2.10.5",
  organization := "me.welcomer",
  excludeFilter in unmanagedResources := HiddenFileFilter || "*secure*.conf" || "*application*.conf",
  scalacOptions := Seq("-feature", "-unchecked", "-deprecation", "-encoding", "utf8"),
  EclipseKeys.skipParents in ThisBuild := false,
  EclipseKeys.withSource := true,
  EclipseKeys.eclipseOutput := Some(".target"),
  EclipseKeys.withBundledScalaContainers := false,
  akkaVersion := "2.3.+",
  sprayVersion := "1.3.+",
  playJsonVersion := "2.3.8",
  resolvers ++= Seq(
    "Typesafe Maven Repository" at "http://repo.typesafe.com/typesafe/maven-releases/",
    //"spray repo" at "http://repo.spray.io",
    //"Kamon Repository" at "http://repo.kamon.io",
    "Sonatype OSS Releases"  at "http://oss.sonatype.org/content/repositories/releases/"
    //"Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
    //"Mandubian Maven Bintray" at "http://dl.bintray.com/mandubian/maven"
  )
)

lazy val root = (project in file("."))
  .settings(bintrayPublishSettings: _*)
  .settings(commonSettings: _*)
  .aggregate(welcomerUtils, welcomerLink, welcomerSignpost)
  .dependsOn(welcomerUtils, welcomerLink)

lazy val welcomerUtils = project.in(file("welcomer-utils"))
  .settings(bintrayPublishSettings: _*)
  .settings(commonSettings: _*)

lazy val welcomerLink = project.in(file("welcomer-link"))
  .settings(bintrayPublishSettings: _*)
  .settings(commonSettings: _*)
  .aggregate(welcomerUtils, welcomerSignpost)
  .dependsOn(welcomerUtils, welcomerSignpost)

lazy val welcomerSignpost = project.in(file("welcomer-signpost"))
  .settings(bintrayPublishSettings: _*)
  .settings(commonSettings: _*)

// TODO: Look into adding this? https://github.com/spray/sbt-revolver

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-async"          % "0.9.2",
  "com.typesafe.akka"      %%  "akka-actor"          % akkaVersion.value,
  //"com.typesafe.akka"      %%  "akka-remote"         % akkaVersion.value,
  "com.typesafe.akka"      %%  "akka-slf4j"          % akkaVersion.value,
  "com.typesafe.akka"      %%  "akka-testkit"        % akkaVersion.value % "test",
  //"io.spray"               %%  "spray-client"        % sprayVersion.value,
  "io.spray"               %%  "spray-can"           % sprayVersion.value,
  "io.spray"               %%  "spray-routing"       % sprayVersion.value,
  "io.spray"               %%  "spray-testkit"       % sprayVersion.value  % "test",
  "org.reactivemongo"      %%  "play2-reactivemongo" % "0.10.5.0.akka23", // Gives us JsValue support instead of BSON
  "com.typesafe.play"      %%  "play-json"           % playJsonVersion.value,
  //"com.mandubian"          %% "play-json-zipper"     % "1.2",
  "ch.qos.logback"         %   "logback-classic"     % "1.1.+",
  //"com.casualmiracles"     %%  "treelog"             % "1.2.2", // We want 1.2.4+ for scalaz 7.1.x but it's only compiled for Scala 2.11.x
  "org.scalaz"             %%  "scalaz-core"         % "7.1.+",
  //"kamon"                  %%  "kamon-spray"         % "0.3.5",
  "org.scalatest"          %%  "scalatest"           % "2.1.6" % "test" // Included by scalamock
  //"org.scalamock"          %% "scalamock-scalatest-support" % "3.1.RC1" % "test", // 3.0.1
)
