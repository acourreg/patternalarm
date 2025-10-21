name := "flink-processor"
version := "0.1.0-SNAPSHOT"
scalaVersion := "2.12.18"

// ✅ Java 9+ module compatibility for tests
Test / javaOptions ++= Seq(
  "--add-opens=java.base/java.util=ALL-UNNAMED",
  "--add-opens=java.base/java.lang=ALL-UNNAMED",
  "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
  "--add-opens=java.base/java.io=ALL-UNNAMED",
  "--add-opens=java.base/java.time=ALL-UNNAMED",  // ✅ AJOUTÉ
  "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED"
)

Test / fork := true

val flinkVersion = "1.18.0"
val jacksonVersion = "2.15.2"

libraryDependencies ++= Seq(
  // Flink dependencies
  "org.apache.flink" %% "flink-streaming-scala" % flinkVersion % "provided",
  "org.apache.flink" % "flink-clients" % flinkVersion % "provided",
  "org.apache.flink" % "flink-connector-kafka" % "3.1.0-1.18",
  "org.apache.flink" % "flink-connector-jdbc" % "3.1.2-1.17",

  // Database
  "org.postgresql" % "postgresql" % "42.7.3",

  // JSON
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion,

  // Testing
  "org.scalatest" %% "scalatest" % "3.2.17" % Test,
  "org.scalamock" %% "scalamock" % "5.2.0" % Test,
  "com.github.tomakehurst" % "wiremock-jre8" % "2.35.0" % Test,
  "com.h2database" % "h2" % "2.2.224" % Test,

  // HTTP
  "com.softwaremill.sttp.client3" %% "core" % "3.9.0",
  "com.softwaremill.sttp.client3" %% "circe" % "3.9.0",

  // Logging
  "org.slf4j" % "slf4j-api" % "2.0.9",
  "org.apache.logging.log4j" % "log4j-slf4j2-impl" % "2.20.0",
  "org.apache.logging.log4j" % "log4j-core" % "2.20.0",

  // Database Connection Pool
  "org.scalikejdbc" %% "scalikejdbc" % "4.0.0",
  "com.zaxxer" % "HikariCP" % "5.1.0",

  // Config
  "com.typesafe" % "config" % "1.4.3"
)

// Assembly settings
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case x => MergeStrategy.first
}