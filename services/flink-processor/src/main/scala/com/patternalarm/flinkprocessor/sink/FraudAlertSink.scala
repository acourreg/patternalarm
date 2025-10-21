package com.patternalarm.flinkprocessor.sink

import com.patternalarm.flinkprocessor.model.{AlertDetail, TransactionEvent}
import com.patternalarm.flinkprocessor.utils.JsonUtils
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import org.slf4j.{Logger, LoggerFactory}
import scalikejdbc._

import java.sql.Timestamp
import java.time.Duration

/**
 * Fraud Alert Sink - Stores alerts and suspicious transactions in PostgreSQL
 * Uses ScalikeJDBC for database operations
 */
class FraudAlertSink(
  jdbcUrl: String,
  username: String,
  password: String
) extends RichSinkFunction[AlertDetail] {

  private val logger: Logger = LoggerFactory.getLogger(classOf[FraudAlertSink])
  private var isH2: Boolean = false

  override def open(parameters: Configuration): Unit = {
    val settings = ConnectionPoolSettings(
      initialSize = 5,
      maxSize = 20,
      connectionTimeoutMillis = 5000L
    )

    ConnectionPool.singleton(jdbcUrl, username, password, settings)
    isH2 = jdbcUrl.contains("jdbc:h2")
    logger.info(s"✅ FraudAlertSink connected to ${if (isH2) "H2" else "PostgreSQL"}")
  }

  override def close(): Unit = {
    ConnectionPool.closeAll()
    logger.info("🔌 FraudAlertSink connection pool closed")
  }

  override def invoke(alertDetail: AlertDetail, context: SinkFunction.Context): Unit =
    try
      DB.localTx { implicit session =>
        // 1. Insert fraud alert
        val alertId: Long = insertAlert(alertDetail)

        // 2. Insert suspicious transactions
        insertTransactions(alertId, alertDetail.transactions)

        logger.info(
          s"✅ Alert $alertId stored: " +
            s"actor=${alertDetail.actorId}, " +
            s"score=${alertDetail.fraudScore}, " +
            s"txns=${alertDetail.transactionCount}"
        )
      }
    catch {
      case e: Exception =>
        logger.error(s"❌ Failed to store alert: ${e.getMessage}", e)
        throw e
    }

  private def insertAlert(alert: AlertDetail)(implicit session: DBSession): Long = {
    val metadataJson = buildMetadataJson(alert)

    if (isH2) {
      sql"""
        INSERT INTO fraud_alerts (
          alert_type, domain, actor_id, severity, fraud_score,
          transaction_count, total_amount, first_seen, last_seen, metadata
        ) VALUES (
          ${alert.alertType}, ${alert.domain}, ${alert.actorId},
          ${alert.severity}, ${alert.fraudScore}, ${alert.transactionCount},
          ${alert.totalAmount}, ${Timestamp.from(alert.firstSeen)},
          ${Timestamp.from(alert.lastSeen)}, ${metadataJson}
        )
      """.updateAndReturnGeneratedKey.apply()
    } else {
      sql"""
        INSERT INTO fraud_alerts (
          alert_type, domain, actor_id, severity, fraud_score,
          transaction_count, total_amount, first_seen, last_seen, metadata
        ) VALUES (
          ${alert.alertType}, ${alert.domain}, ${alert.actorId},
          ${alert.severity}, ${alert.fraudScore}, ${alert.transactionCount},
          ${alert.totalAmount}, ${Timestamp.from(alert.firstSeen)},
          ${Timestamp.from(alert.lastSeen)}, ${metadataJson}::jsonb
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
          ${tx.transactionId}, ${tx.domain}, ${tx.testId}, ${Timestamp.from(tx.timestamp)}, ${tx.actorId},
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

  private def buildMetadataJson(alert: AlertDetail): String = {
    val windowSeconds = Duration
      .between(alert.firstSeen, alert.lastSeen)
      .getSeconds

    JsonUtils.toJson(Map(
      "window_seconds" -> windowSeconds,
      "confidence" -> alert.fraudScore,
      "alert_type" -> alert.alertType,
      "severity" -> alert.severity,
      "model_version" -> alert.modelVersion,
      "inference_time_ms" -> alert.inferenceTimeMs
    ))
  }
}
