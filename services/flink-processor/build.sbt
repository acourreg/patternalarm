import java.nio.file.Paths

name := "flink-processor"
version := "1.0.0"
scalaVersion := "2.12.18"

// Versions
val flinkVersion = "1.18.0"
val jacksonVersion = "2.15.2"
val scalaTestVersion = "3.2.17"

// ========== Avro Code Generation (Manual Task Only) ==========
enablePlugins(SbtAvrohugger)

// Manual task to generate Avro classes (only when schemas change)
lazy val avroGenerate = taskKey[Unit]("Generate Scala classes from Avro schemas")

avroGenerate := {
  val projectRoot = Paths.get("").toAbsolutePath.getParent.getParent
  val schemasDir = projectRoot.resolve("scripts/schemas/avro").toFile
  
  if (!schemasDir.exists()) {
    println(s"[WARN] Avro schemas directory not found: ${schemasDir.getAbsolutePath}")
    println("[WARN] Skipping Avro generation. Run this task from project root context.")
  } else {
    println(s"[INFO] Generating Avro classes from: ${schemasDir.getAbsolutePath}")
    
    // Temporarily set avro source directory
    (Compile / avroSourceDirectories) := Seq(schemasDir)
    
    // Copy generated files to src/main/scala
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
        println(s"[INFO] Generated: ${file.getName}")
      }
    }
    
    println("[INFO] Avro generation complete")
  }
}

// Don't trigger Avro generation during normal build
Compile / avroSourceDirectories := Seq.empty

// ========== Dependencies ==========
libraryDependencies ++= Seq(
  // Flink Core
  "org.apache.flink" %% "flink-streaming-scala" % flinkVersion,
  "org.apache.flink" % "flink-clients" % flinkVersion,

  // Flink Connectors
  "org.apache.flink" % "flink-connector-kafka" % "3.1.0-1.18",
  "org.apache.flink" % "flink-connector-jdbc" % "3.1.2-1.17",

  // Database
  "org.postgresql" % "postgresql" % "42.7.3",

  // JSON Serialization
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
  "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion,

  // Testing
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  "org.scalamock" %% "scalamock" % "5.2.0" % Test,
  "com.github.tomakehurst" % "wiremock-jre8" % "2.35.0" % Test,
  "com.h2database" % "h2" % "2.2.224" % Test,

  // HTTP Client
  "com.softwaremill.sttp.client3" %% "okhttp-backend" % "3.9.0",

  // Logging
  "org.slf4j" % "slf4j-api" % "2.0.9",
  "org.apache.logging.log4j" % "log4j-slf4j2-impl" % "2.20.0",
  "org.apache.logging.log4j" % "log4j-core" % "2.20.0",

  // ScalikeJDBC
  "org.scalikejdbc" %% "scalikejdbc" % "4.0.0",
  "com.zaxxer" % "HikariCP" % "5.1.0",

  // Config
  "com.typesafe" % "config" % "1.4.3"
)

// ========== Assembly (Fat JAR) Config ==========
assembly / assemblyJarName := s"${name.value}-assembly-${version.value}.jar"

assembly / mainClass := Some("com.patternalarm.flinkprocessor.StreamProcessorJob")

// Include all dependencies in fat JAR
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList("META-INF", "services", xs @ _*) => MergeStrategy.concat
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case "log4j2.properties" => MergeStrategy.first
  case x if x.endsWith(".proto") => MergeStrategy.first
  case x if x.contains("module-info") => MergeStrategy.discard
  case x if x.endsWith(".class") => MergeStrategy.first
  case x if x.endsWith(".properties") => MergeStrategy.first
  case x if x.endsWith(".xml") => MergeStrategy.first
  case x if x.endsWith(".dtd") => MergeStrategy.first
  case x if x.endsWith(".xsd") => MergeStrategy.first
  case "plugin.properties" => MergeStrategy.first
  case "META-INF/io.netty.versions.properties" => MergeStrategy.first
  case PathList("org", "apache", "commons", xs @ _*) => MergeStrategy.first
  case x =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}

// Include all dependencies (don't exclude Flink for fat JAR)
assembly / assemblyExcludedJars := {
  val cp = (assembly / fullClasspath).value
  cp.filter { jar =>
    false  // Include everything
  }
}

// Don't run tests during assembly
assembly / test := {}
