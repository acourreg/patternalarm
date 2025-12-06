package com.patternalarm.flinkprocessor.sink

import com.patternalarm.flinkprocessor.model.{Alert, TransactionEvent}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import org.slf4j.{Logger, LoggerFactory}
import scalikejdbc._

import java.sql.Timestamp
import java.time.Instant

// Add companion object for stats
object FraudAlertSink {
  var totalInserts = 0L
}

/**
 * Fraud Alert Sink - Stores alerts and suspicious transactions in PostgreSQL
 * Uses ScalikeJDBC for database operations
 *
 * Input: (Alert, Seq[TransactionEvent])
 */
class FraudAlertSink(
  jdbcUrl: String,
  username: String,
  password: String
) extends RichSinkFunction[(Alert, Seq[TransactionEvent])] {

  private val logger: Logger = LoggerFactory.getLogger(classOf[FraudAlertSink])

  // ✅ Determine database type from URL
  private val isH2: Boolean = jdbcUrl.toLowerCase.contains("jdbc:h2")

  override def open(parameters: Configuration): Unit = {
    val settings = ConnectionPoolSettings(
      initialSize = 5,
      maxSize = 20,
      connectionTimeoutMillis = 5000L
    )

    ConnectionPool.singleton(jdbcUrl, username, password, settings)
    logger.info(s"✅ FraudAlertSink connected to ${if (isH2) "H2" else "PostgreSQL"}")
  }

  override def close(): Unit = {
    ConnectionPool.closeAll()
    logger.info("🔌 FraudAlertSink connection pool closed")
  }

  override def invoke(value: (Alert, Seq[TransactionEvent]), context: SinkFunction.Context): Unit = {
    val (alert, transactions) = value

    val startMs = System.currentTimeMillis()

    try {
      DB.localTx { implicit session =>
        val alertId: Long = insertAlert(alert)
        insertTransactions(alertId, transactions)

        val durationMs = System.currentTimeMillis() - startMs

        // ✅ Always log INSERT success
        logger.info(
          s"✅ INSERT alert_id=$alertId | " +
            s"actor=${alert.actorId} | " +
            s"score=${alert.fraudScore} | " +
            s"severity=${alert.severity} | " +
            s"txns=${transactions.size} | " +
            s"${durationMs}ms"
        )

        // Track totals
        FraudAlertSink.totalInserts += 1
        if (FraudAlertSink.totalInserts % 10 == 0) {
          logger.info(s"📈 SINK STATS: total_inserts=${FraudAlertSink.totalInserts}")
        }
      }
    } catch {
      case e: Exception =>
        logger.error(s"❌ INSERT FAILED: actor=${alert.actorId} | error=${e.getMessage}", e)
        throw e
    }
  }

  private def insertAlert(alert: Alert)(implicit session: DBSession): Long = {
    // ✅ H2: Store patterns as comma-separated string
    val patternsString = alert.patternsDetected.map(_.mkString(",")).orNull

    if (isH2) {
      sql"""
        INSERT INTO fraud_alerts (
          alert_type, domain, actor_id, severity, fraud_score,
          transaction_count, total_amount, first_seen, last_seen,
          window_seconds, baseline_avg, patterns_detected, confidence, model_version, inference_time_ms,
          player_id, game_id, item_type, item_name, session_length_sec,
          account_from, account_to, transfer_type, country_from, country_to, purpose,
          user_id, cart_items, shipping_address, billing_address, device_fingerprint, session_duration_sec,
          payment_method, device_id, ip_address
        ) VALUES (
          ${alert.alertType}, ${alert.domain}, ${alert.actorId},
          ${alert.severity}, ${alert.fraudScore}, ${alert.transactionCount},
          ${alert.totalAmount}, ${toTimestamp(alert.firstSeen)},
          ${toTimestamp(alert.lastSeen)},
          ${alert.windowSeconds.map(Long.box).orNull}, ${alert.baselineAvg.map(Double.box).orNull},
          ${patternsString}, ${alert.confidence.map(Int.box).orNull},
          ${alert.modelVersion.orNull}, ${alert.inferenceTimeMs.map(Int.box).orNull},
          ${alert.playerId.orNull}, ${alert.gameId.orNull}, ${alert.itemType.orNull},
          ${alert.itemName.orNull}, ${alert.sessionLengthSec.map(Int.box).orNull},
          ${alert.accountFrom.orNull}, ${alert.accountTo.orNull}, ${alert.transferType.orNull},
          ${alert.countryFrom.orNull}, ${alert.countryTo.orNull}, ${alert.purpose.orNull},
          ${alert.userId.orNull}, ${alert.cartItems.orNull}, ${alert.shippingAddress.orNull},
          ${alert.billingAddress.orNull}, ${alert.deviceFingerprint.orNull},
          ${alert.sessionDurationSec.map(Int.box).orNull},
          ${alert.paymentMethod.orNull}, ${alert.deviceId.orNull}, ${alert.ipAddress.orNull}
        )
      """.updateAndReturnGeneratedKey.apply()
    } else {
      // PostgreSQL with array support for patternsDetected
      val patternsArray = alert.patternsDetected.map(_.toArray).orNull

      sql"""
        INSERT INTO fraud_alerts (
          alert_type, domain, actor_id, severity, fraud_score,
          transaction_count, total_amount, first_seen, last_seen,
          window_seconds, baseline_avg, patterns_detected, confidence, model_version, inference_time_ms,
          player_id, game_id, item_type, item_name, session_length_sec,
          account_from, account_to, transfer_type, country_from, country_to, purpose,
          user_id, cart_items, shipping_address, billing_address, device_fingerprint, session_duration_sec,
          payment_method, device_id, ip_address
        ) VALUES (
          ${alert.alertType}, ${alert.domain}, ${alert.actorId},
          ${alert.severity}, ${alert.fraudScore}, ${alert.transactionCount},
          ${alert.totalAmount}, ${toTimestamp(alert.firstSeen)},
          ${toTimestamp(alert.lastSeen)},
          ${alert.windowSeconds.map(Long.box).orNull}, ${alert.baselineAvg.map(Double.box).orNull},
          ${patternsArray}, ${alert.confidence.map(Int.box).orNull},
          ${alert.modelVersion.orNull}, ${alert.inferenceTimeMs.map(Int.box).orNull},
          ${alert.playerId.orNull}, ${alert.gameId.orNull}, ${alert.itemType.orNull},
          ${alert.itemName.orNull}, ${alert.sessionLengthSec.map(Int.box).orNull},
          ${alert.accountFrom.orNull}, ${alert.accountTo.orNull}, ${alert.transferType.orNull},
          ${alert.countryFrom.orNull}, ${alert.countryTo.orNull}, ${alert.purpose.orNull},
          ${alert.userId.orNull}, ${alert.cartItems.orNull}, ${alert.shippingAddress.orNull},
          ${alert.billingAddress.orNull}, ${alert.deviceFingerprint.orNull},
          ${alert.sessionDurationSec.map(Int.box).orNull},
          ${alert.paymentMethod.orNull}, ${alert.deviceId.orNull}, ${alert.ipAddress.orNull}
        )
        RETURNING alert_id
      """.map(_.long("alert_id")).single.apply().getOrElse {
        throw new RuntimeException("Failed to retrieve alert_id")
      }
    }
  }

  private def insertTransactions(
    alertId: Long,
    transactions: Seq[TransactionEvent]
  )(implicit session: DBSession): Unit =
    transactions.foreach { tx =>
      sql"""
        INSERT INTO suspicious_transactions (
          alert_id,
          transaction_id, domain, test_id, timestamp, actor_id,
          amount, currency, ip_address,
          pattern, is_fraud, sequence_position,
          player_id, game_id, item_type, item_name, payment_method, device_id, session_length_sec,
          account_from, account_to, transfer_type, country_from, country_to, purpose,
          user_id, cart_items, shipping_address, billing_address, device_fingerprint, session_duration_sec
        ) VALUES (
          ${alertId},
          ${tx.transactionId}, ${tx.domain}, ${tx.testId}, ${toTimestamp(tx.timestamp)}, ${tx.actorId},
          ${tx.amount}, ${tx.currency}, ${tx.ipAddress},
          ${tx.pattern}, ${tx.isFraud}, ${tx.sequencePosition},
          ${tx.playerId.orNull}, ${tx.gameId.orNull}, ${tx.itemType.orNull}, ${tx.itemName.orNull},
          ${tx.paymentMethod.orNull}, ${tx.deviceId.orNull}, ${tx.sessionLengthSec.map(Int.box).orNull},
          ${tx.accountFrom.orNull}, ${tx.accountTo.orNull}, ${tx.transferType.orNull},
          ${tx.countryFrom.orNull}, ${tx.countryTo.orNull}, ${tx.purpose.orNull},
          ${tx.userId.orNull}, ${tx.cartItems.orNull}, ${tx.shippingAddress.orNull},
          ${tx.billingAddress.orNull}, ${tx.deviceFingerprint.orNull}, ${tx.sessionDurationSec.map(Int.box).orNull}
        )
      """.update.apply()
    }

  // ✅ Helper: Convert Instant to SQL Timestamp
  private def toTimestamp(instant: Instant): Timestamp =
    Timestamp.from(instant)
}
