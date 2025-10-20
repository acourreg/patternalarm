package com.patternalarm.flinkprocessor.sink

import com.patternalarm.flinkprocessor.model.{Alert, TransactionEvent}
import com.patternalarm.flinkprocessor.utils.JsonUtils
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.sink.{RichSinkFunction, SinkFunction}
import org.slf4j.{Logger, LoggerFactory}
import scalikejdbc._

import java.sql.Timestamp

class FraudAlertSink(
  jdbcUrl: String,
  username: String,
  password: String
) extends RichSinkFunction[Alert] {

  private val logger: Logger = LoggerFactory.getLogger(classOf[FraudAlertSink])

  override def open(parameters: Configuration): Unit = {
    val settings = ConnectionPoolSettings(
      initialSize = 5,
      maxSize = 20,
      connectionTimeoutMillis = 5000L
    )

    ConnectionPool.singleton(jdbcUrl, username, password, settings)
    logger.info("✅ FraudAlertSink connected to PostgreSQL via ScalikeJDBC")
  }

  override def close(): Unit =
    ConnectionPool.closeAll()

//  override def invoke(
//                       alertData: FraudAlert,
//                       context: SinkFunction.Context
//                     ): Unit = {
//
//    val agg = alertData.aggregate
//    val resp = alertData.response
//
//    try {
//      DB.localTx { implicit session =>
//
//        val alertId: Long = sql"""
//          INSERT INTO fraud_alerts (
//            alert_type, domain, actor_id, severity, fraud_score,
//            transaction_count, total_amount, first_seen, last_seen, metadata
//          ) VALUES (
//            ${alertData.alertType}, ${agg.domain}, ${agg.actorId},
//            ${alertData.severity}, ${resp.fraudScore}, ${agg.transactionCount},
//            ${agg.totalAmount},
//            ${Timestamp.from(agg.windowStart)},
//            ${Timestamp.from(agg.windowEnd)},
//            ${buildMetadataJson(agg, resp)}::jsonb
//          )
//        """.updateAndReturnGeneratedKey.apply()
//
//        val batchParams = agg.transactions.map { txn =>
//          Seq(
//            txn.transactionId,
//            alertId,
//            txn.actorId,
//            txn.domain,
//            Timestamp.from(txn.timestamp),
//            txn.amount,
//            buildTransactionDataJson(txn)  // ← Enriched version
//          )
//        }
//
//        sql"""
//          INSERT INTO suspicious_transactions (
//            transaction_id, alert_id, actor_id, domain, timestamp, amount, transaction_data
//          ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
//        """.batch(batchParams: _*).apply()
//
//        logger.info(s"✅ Alert $alertId stored: actor=${agg.actorId}, score=${resp.fraudScore}, txns=${agg.transactionCount}")
//      }
//
//    } catch {
//      case e: Exception =>
//        logger.error(s"❌ Failed to store alert: ${e.getMessage}")
//        throw e
//    }
//  }
//
//  private def buildMetadataJson(agg: TimedWindowAggregate, resp: PredictResponse): String = {
//    val windowSeconds = java.time.Duration
//      .between(agg.windowStart, agg.windowEnd)
//      .getSeconds
//
//    val patterns = agg.transactions
//      .filter(_.isFraud)
//      .map(_.pattern)
//      .distinct
//
//    JsonUtils.toJson(Map(
//      "window_seconds" -> windowSeconds,
//      "baseline_avg" -> 1.2,
//      "patterns_detected" -> patterns,
//      "confidence" -> resp.fraudScore,
//      "model_version" -> resp.modelVersion,
//      "inference_time_ms" -> resp.inferenceTimeMs
//    ))
//  }

  /**
   * Build enriched transaction_data with ALL domain-specific fields
   */
  private def buildTransactionDataJson(txn: TransactionEvent): String = {
    // Base data (all domains)
    val baseData = Map(
      "ip_address" -> txn.ipAddress,
      "currency" -> txn.currency,
      "pattern" -> txn.pattern,
      "is_fraud" -> txn.isFraud,
      "sequence_position" -> txn.sequencePosition
    )

    // Domain-specific data
    val domainData: Map[String, Any] = txn.domain match {
      case "gaming" => Map(
          "player_id" -> txn.playerId.orNull,
          "game_id" -> txn.gameId.orNull,
          "item_type" -> txn.itemType.orNull,
          "item_name" -> txn.itemName.orNull,
          "payment_method" -> txn.paymentMethod.orNull,
          "device_id" -> txn.deviceId.orNull,
          "session_length_sec" -> txn.sessionLengthSec.orNull
        )

      case "fintech" => Map(
          "account_from" -> txn.accountFrom.orNull,
          "account_to" -> txn.accountTo.orNull,
          "transfer_type" -> txn.transferType.orNull,
          "country_from" -> txn.countryFrom.orNull,
          "country_to" -> txn.countryTo.orNull,
          "purpose" -> txn.purpose.orNull,
          "payment_method" -> "bank_transfer"
        )

      case "ecommerce" => Map(
          "user_id" -> txn.userId.orNull,
          "cart_items" -> txn.cartItems.orNull,
          "shipping_address" -> txn.shippingAddress.orNull,
          "billing_address" -> txn.billingAddress.orNull,
          "device_id" -> txn.deviceFingerprint.orNull,
          "payment_method" -> txn.paymentMethod.orNull,
          "session_duration_sec" -> txn.sessionDurationSec.orNull
        )

      case _ => Map.empty
    }

    // Merge and filter nulls
    val allData = (baseData ++ domainData).filter {
      case (_, null) => false
      case (_, None) => false
      case _ => true
    }

    JsonUtils.toJson(allData)
  }
}
