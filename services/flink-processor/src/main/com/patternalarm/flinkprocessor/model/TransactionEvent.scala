package com.patternalarm.flinkprocessor.model

import java.time.Instant
import com.patternalarm.flinkprocessor.model.FraudType.FraudType

/**
 * Raw transaction event from Kafka
 * Supports all 3 domains: gaming, fintech, ecommerce
 */
case class TransactionEvent(
     // === Common fields (all domains) ===
     transactionId: String,
     domain: String,
     testId: String,
     timestamp: Instant,
     actorId: String,
     amount: Double,
     currency: String,
     ipAddress: String,

     // Pattern metadata
     pattern: String,
     isFraud: Boolean,
     sequencePosition: Int,

     // === Gaming-specific fields ===
     playerId: Option[String] = None,
     gameId: Option[String] = None,
     itemType: Option[String] = None,
     itemName: Option[String] = None,
     paymentMethod: Option[String] = None,
     deviceId: Option[String] = None,
     sessionLengthSec: Option[Int] = None,

     // === Fintech-specific fields ===
     accountFrom: Option[String] = None,
     accountTo: Option[String] = None,
     transferType: Option[String] = None,
     countryFrom: Option[String] = None,
     countryTo: Option[String] = None,
     purpose: Option[String] = None,

     // === Ecommerce-specific fields ===
     userId: Option[String] = None,
     cartItems: Option[String] = None,  // JSON string array
     shippingAddress: Option[String] = None,
     billingAddress: Option[String] = None,
     deviceFingerprint: Option[String] = None,
     sessionDurationSec: Option[Int] = None
   ) {
  /**
   * Get fraud type enum from pattern
   */
  def fraudType: FraudType = FraudType.fromPattern(pattern)
}

object TransactionEvent {
  /**
   * Extract device identifier (gaming or ecommerce)
   */
  def getDeviceId(event: TransactionEvent): Option[String] = {
    event.deviceId.orElse(event.deviceFingerprint)
  }
}