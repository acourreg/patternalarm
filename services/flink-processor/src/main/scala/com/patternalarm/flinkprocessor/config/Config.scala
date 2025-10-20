package com.patternalarm.flinkprocessor.config

import com.typesafe.config.{Config, ConfigFactory}

/**
 * Application configuration loaded from application.conf
 * Supports environment variable overrides (${?ENV_VAR})
 */
object Config {

  private val config: Config = ConfigFactory.load()
  private val root = config.getConfig("patternalarm")

  // ========== Kafka ==========
  object Kafka {
    private val kafka = root.getConfig("kafka")

    val bootstrapServers: String = kafka.getString("bootstrap-servers")
    val topic: String = kafka.getString("topic")
    val groupId: String = kafka.getString("group-id")
    val autoOffsetReset: String = kafka.getString("auto-offset-reset")
  }

  // ========== FastAPI ==========
  object FastApi {
    private val fastapi = root.getConfig("fastapi")

    val url: String = fastapi.getString("url")
    val timeoutMs: Long = fastapi.getLong("timeout-ms")
    val maxConcurrentRequests: Int = fastapi.getInt("max-concurrent-requests")
  }

  // ========== Database ==========
  object Database {
    private val db = root.getConfig("database")

    val url: String = db.getString("url")
    val user: String = db.getString("user")
    val password: String = db.getString("password")

    object Pool {
      private val pool = db.getConfig("pool")

      val initialSize: Int = pool.getInt("initial-size")
      val maxSize: Int = pool.getInt("max-size")
      val connectionTimeoutMs: Long = pool.getLong("connection-timeout-ms")
    }
  }

  // ========== Flink ==========
  object Flink {
    private val flink = root.getConfig("flink")

    val checkpointingIntervalMs: Long = flink.getLong("checkpointing-interval-ms")

    object Windowing {
      private val windowing = flink.getConfig("windowing")

      val sizeMinutes: Int = windowing.getInt("size-minutes")
      val latenessSeconds: Int = windowing.getInt("lateness-seconds")
    }

    object FraudDetection {
      private val fraud = flink.getConfig("fraud-detection")

      val scoreThreshold: Int = fraud.getInt("score-threshold")
    }
  }

  // ========== Helper: Print config summary ==========
  def printSummary(): Unit =
    println(s"""
               |🔧 PatternAlarm Configuration
               |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
               |Kafka:
               |  Bootstrap: ${Kafka.bootstrapServers}
               |  Topic:     ${Kafka.topic}
               |  Group ID:  ${Kafka.groupId}
               |
               |FastAPI:
               |  URL:       ${FastApi.url}
               |  Timeout:   ${FastApi.timeoutMs}ms
               |
               |Database:
               |  URL:       ${Database.url}
               |  User:      ${Database.user}
               |  Pool Size: ${Database.Pool.initialSize}-${Database.Pool.maxSize}
               |
               |Flink:
               |  Checkpoint:      ${Flink.checkpointingIntervalMs}ms
               |  Window Size:     ${Flink.Windowing.sizeMinutes}min
               |  Fraud Threshold: ${Flink.FraudDetection.scoreThreshold}
               |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
               |""".stripMargin)
}
