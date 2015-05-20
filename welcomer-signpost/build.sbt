name := "welcomer-signpost"

version := "0.1.0"

lazy val akkaVersion = settingKey[String]("The version of Akka used for building.")

lazy val sprayVersion = settingKey[String]("The version of Spray used for building.")

libraryDependencies ++= Seq(
  "com.typesafe.akka"   %%  "akka-slf4j"          % akkaVersion.value,
  "io.spray"            %%  "spray-client"        % sprayVersion.value,
  "oauth.signpost"      %   "signpost-core"       % "1.2.+"
)
