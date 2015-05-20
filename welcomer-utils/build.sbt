name := "welcomer-utils"

lazy val playJsonVersion = settingKey[String]("The version of Play Json used for building.")

libraryDependencies ++= Seq(
  "org.reactivemongo"   %%  "play2-reactivemongo" % "0.10.5.0.akka23", // Gives us JsValue support instead of BSON
  "com.typesafe.play"   %%  "play-json"           % playJsonVersion.value,
  "org.apache.commons"  %   "commons-lang3"       % "3.3.+"
)
