import java.nio.file.Paths

name := "flink-processor"
version := "1.0.0"
scalaVersion := "2.12.18"

val flinkVersion = "1.18.0"
val jacksonVersion = "2.15.2"

// ========== Avro Code Generation ==========
enablePlugins(SbtAvrohugger)

val projectRoot = Paths.get("").toAbsolutePath.getParent.getParent
val schemasDir = projectRoot.resolve("scripts/schemas/avro").toFile

Compile / avroSourceDirectories := Seq(schemasDir)

// Auto-copy generated Avro models to src/main/scala
Compile / compile := (Compile / compile).dependsOn(Def.task {
  val srcManaged = (Compile / sourceManaged).value / "main" / "compiled_avro" / "com" / "patternalarm" / "flinkprocessor" / "model"
  val targetDir = (Compile / scalaSource).value / "com" / "patternalarm" / "flinkprocessor" / "model"

  if (srcManaged.exists()) {
    val customFiles = Set("TimedWindowAggregate.scala", "PredictRequest.scala", "PredictResponse.scala")

    IO.createDirectory(targetDir)
    if (targetDir.exists()) {
      (targetDir * "*.scala").get.filterNot(f => customFiles.contains(f.getName)).foreach(IO.delete)
    }

    (srcManaged * "*.scala").get.foreach { file =>
      IO.copyFile(file, targetDir / file.getName)
    }
  }
}).value

// ========== Dependencies ==========
libraryDependencies ++= Seq(
  // Flink Core
  "org.apache.flink" %% "flink-streaming-scala" % flinkVersion % "provided",
  "org.apache.flink" % "flink-clients" % flinkVersion % "provided",

  // Kafka Connector
  "org.apache.flink" % "flink-connector-kafka" % "3.1.0-1.18",

  // JDBC Connector
  "org.apache.flink" % "flink-connector-jdbc" % "3.1.2-1.17",
  "org.postgresql" % "postgresql" % "42.6.0",

  // JSON Serialization
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion,

  // Testing
  "org.scalatest" %% "scalatest" % "3.2.17" % Test,
  "org.scalamock" %% "scalamock" % "5.2.0" % Test,
  "com.github.tomakehurst" % "wiremock-jre8" % "2.35.0" % Test,

  // HTTP Client
  "com.softwaremill.sttp.client3" %% "core" % "3.9.0",
  "com.softwaremill.sttp.client3" %% "circe" % "3.9.0",

  // Logging
  "org.slf4j" % "slf4j-api" % "2.0.9",
  "org.apache.logging.log4j" % "log4j-slf4j2-impl" % "2.20.0" % "runtime",
  "org.apache.logging.log4j" % "log4j-core" % "2.20.0" % "runtime",

  // ScalikeJDBC
  "org.scalikejdbc" %% "scalikejdbc" % "4.0.0",
  "org.postgresql" % "postgresql" % "42.7.3",
  "com.zaxxer" % "HikariCP" % "5.1.0",

  // Config
  "com.typesafe" % "config" % "1.4.3"
)

// ========== Assembly ==========
assembly / assemblyJarName := "flink-processor.jar"

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case _ => MergeStrategy.first
}

assembly / assemblyExcludedJars := {
  (assembly / fullClasspath).value.filter { f =>
    f.data.getName.contains("flink-dist") ||
      f.data.getName.contains("flink-streaming-scala") ||
      f.data.getName.contains("flink-clients")
  }
}