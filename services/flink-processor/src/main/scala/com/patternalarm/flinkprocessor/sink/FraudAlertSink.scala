package com.patternalarm.flinkprocessor.sink

import com.patternalarm.flinkprocessor.model.Alert
import com.patternalarm.flinkprocessor.utils.JsonUtils
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import org.slf4j.{Logger, LoggerFactory}
import scalikejdbc._

import java.sql.Timestamp
import java.time.Duration

/**
 * Fraud Alert Sink - Stores alerts in PostgreSQL
 * Uses ScalikeJDBC for database operations
 */
class FraudAlertSink(
  jdbcUrl: String,
  username: String,
  password: String
) extends RichSinkFunction[Alert] {

  private val logger: Logger = LoggerFactory.getLogger(classOf[FraudAlertSink])
  private var isH2: Boolean = false

  override def open(parameters: Configuration): Unit = {
    val settings = ConnectionPoolSettings(
      initialSize = 5,
      maxSize = 20,
      connectionTimeoutMillis = 5000L
    )

    ConnectionPool.singleton(jdbcUrl, username, password, settings)

    // Detect if we're using H2 (for tests) or PostgreSQL (production)
    isH2 = jdbcUrl.contains("jdbc:h2")

    logger.info(s"✅ FraudAlertSink connected to ${if (isH2) "H2" else "PostgreSQL"}")
  }

  override def close(): Unit = {
    ConnectionPool.closeAll()
    logger.info("🔌 FraudAlertSink connection pool closed")
  }

  override def invoke(alert: Alert, context: SinkFunction.Context): Unit =
    try
      DB.localTx { implicit session =>
        val metadataJson = buildMetadataJson(alert)

        val alertId: Long = if (isH2) {
          // H2: No ::jsonb cast, simpler RETURNING syntax
          sql"""
            INSERT INTO fraud_alerts (
              alert_type, domain, actor_id, severity, fraud_score,
              transaction_count, total_amount, first_seen, last_seen, metadata
            ) VALUES (
              ${alert.alertType},
              ${alert.domain},
              ${alert.actorId},
              ${alert.severity},
              ${alert.fraudScore},
              ${alert.transactionCount},
              ${alert.totalAmount},
              ${Timestamp.from(alert.firstSeen)},
              ${Timestamp.from(alert.lastSeen)},
              ${metadataJson}
            )
          """.updateAndReturnGeneratedKey.apply()
        } else {
          // PostgreSQL: Use ::jsonb cast
          sql"""
            INSERT INTO fraud_alerts (
              alert_type, domain, actor_id, severity, fraud_score,
              transaction_count, total_amount, first_seen, last_seen, metadata
            ) VALUES (
              ${alert.alertType},
              ${alert.domain},
              ${alert.actorId},
              ${alert.severity},
              ${alert.fraudScore},
              ${alert.transactionCount},
              ${alert.totalAmount},
              ${Timestamp.from(alert.firstSeen)},
              ${Timestamp.from(alert.lastSeen)},
              ${metadataJson}::jsonb
            )
            RETURNING alert_id
          """.map(_.long("alert_id")).single.apply().getOrElse {
            throw new RuntimeException("Failed to retrieve alert_id")
          }
        }

        logger.info(
          s"✅ Alert $alertId stored: " +
            s"actor=${alert.actorId}, " +
            s"score=${alert.fraudScore}, " +
            s"txns=${alert.transactionCount}"
        )
      }
    catch {
      case e: Exception =>
        logger.error(s"❌ Failed to store alert: ${e.getMessage}", e)
        throw e
    }

  private def buildMetadataJson(alert: Alert): String = {
    val windowSeconds = Duration
      .between(alert.firstSeen, alert.lastSeen)
      .getSeconds

    JsonUtils.toJson(Map(
      "window_seconds" -> windowSeconds,
      "confidence" -> alert.fraudScore,
      "alert_type" -> alert.alertType,
      "severity" -> alert.severity
    ))
  }
}
