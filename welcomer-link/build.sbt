name := "welcomer-link"

lazy val akkaVersion = settingKey[String]("The version of Akka used for building.")

lazy val sprayVersion = settingKey[String]("The version of Spray used for building.")

lazy val playJsonVersion = settingKey[String]("The version of Play Json used for building.")

libraryDependencies ++= Seq(
  "com.typesafe.akka"   %%  "akka-slf4j"          % akkaVersion.value,
  "io.spray"            %%  "spray-client"        % sprayVersion.value,
  "com.typesafe.play"   %%  "play-json"           % playJsonVersion.value,
  "org.bouncycastle"    %   "bcpkix-jdk15on"      % "1.+",
  "com.amazonaws"       %   "aws-java-sdk-kms"    % "1.9.+"
)
