name := "flink-processor"
version := "1.0.0"
scalaVersion := "2.12.18"

val flinkVersion = "1.18.0"
val jacksonVersion = "2.15.2"
val circeVersion = "0.14.6"

libraryDependencies ++= Seq(
  // Flink Core
  "org.apache.flink" %% "flink-streaming-scala" % flinkVersion % "provided",
  "org.apache.flink" % "flink-clients" % flinkVersion % "provided",

  // Kafka Connector (has -1.18 suffix)
  "org.apache.flink" % "flink-connector-kafka" % "3.1.0-1.18",

  // JDBC Connector (NO -1.18 suffix!)
  "org.apache.flink" % "flink-connector-jdbc" % "3.1.2-1.17",  // ← Fixed
  "org.postgresql" % "postgresql" % "42.6.0",

  // JSON Serialization
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion,

  // Circe
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,

  // HTTP Client
  "com.softwaremill.sttp.client3" %% "core" % "3.9.0",
  "com.softwaremill.sttp.client3" %% "circe" % "3.9.0",

  // Logging
  "org.slf4j" % "slf4j-api" % "2.0.9",
  "org.apache.logging.log4j" % "log4j-slf4j2-impl" % "2.20.0" % "runtime",
  "org.apache.logging.log4j" % "log4j-core" % "2.20.0" % "runtime"
)

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.5")

// Assembly settings for fat JAR
assemblyJarName in assembly := "flink-processor.jar"

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case x => MergeStrategy.first
}

// Exclude provided dependencies from fat JAR
assemblyExcludedJars in assembly := {
  val cp = (fullClasspath in assembly).value
  cp.filter { f =>
    f.data.getName.contains("flink-dist") ||
      f.data.getName.contains("flink-streaming-scala") ||
      f.data.getName.contains("flink-clients")
  }
}