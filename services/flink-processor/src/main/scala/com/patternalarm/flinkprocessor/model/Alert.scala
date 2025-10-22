/** MACHINE-GENERATED FROM AVRO SCHEMA. DO NOT EDIT DIRECTLY */
package com.patternalarm.flinkprocessor.model

/**
 * Fraud alert with all metadata and context
 * @param alertId Alert ID (auto-generated)
 * @param alertType Alert type (velocity_spike, etc.)
 * @param domain Domain: gaming, fintech, or ecommerce
 * @param actorId Actor/user performing transactions
 * @param severity Severity: LOW, MEDIUM, HIGH, CRITICAL
 * @param fraudScore Fraud score (0-100)
 * @param transactionCount Number of transactions in window
 * @param totalAmount Total transaction amount
 * @param firstSeen First transaction timestamp
 * @param lastSeen Last transaction timestamp
 * @param windowSeconds Window duration in seconds
 * @param baselineAvg Baseline average for comparison
 * @param patternsDetected List of fraud patterns detected
 * @param confidence Confidence score
 * @param modelVersion ML model version
 * @param inferenceTimeMs ML inference time (ms)
 * @param playerId Gaming: Player ID
 * @param gameId Gaming: Game ID
 * @param itemType Gaming: Item type
 * @param itemName Gaming: Item name
 * @param sessionLengthSec Gaming: Session length
 * @param accountFrom Fintech: Source account
 * @param accountTo Fintech: Destination account
 * @param transferType Fintech: Transfer type
 * @param countryFrom Fintech: Source country
 * @param countryTo Fintech: Destination country
 * @param purpose Fintech: Transaction purpose
 * @param userId Ecommerce: User ID
 * @param cartItems Ecommerce: Cart items (JSON string)
 * @param shippingAddress Ecommerce: Shipping address
 * @param billingAddress Ecommerce: Billing address
 * @param deviceFingerprint Ecommerce: Device fingerprint
 * @param sessionDurationSec Ecommerce: Session duration
 * @param paymentMethod Payment method (cross-domain)
 * @param deviceId Device ID (cross-domain)
 * @param ipAddress IP address (cross-domain)
 */
final case class Alert(
  alertId: Int,
  alertType: String,
  domain: String,
  actorId: String,
  severity: String,
  fraudScore: Int,
  transactionCount: Int,
  totalAmount: Double,
  firstSeen: java.time.Instant,
  lastSeen: java.time.Instant,
  windowSeconds: Option[Long] = None,
  baselineAvg: Option[Double] = None,
  patternsDetected: Option[Seq[String]] = None,
  confidence: Option[Int] = None,
  modelVersion: Option[String] = None,
  inferenceTimeMs: Option[Int] = None,
  playerId: Option[String] = None,
  gameId: Option[String] = None,
  itemType: Option[String] = None,
  itemName: Option[String] = None,
  sessionLengthSec: Option[Int] = None,
  accountFrom: Option[String] = None,
  accountTo: Option[String] = None,
  transferType: Option[String] = None,
  countryFrom: Option[String] = None,
  countryTo: Option[String] = None,
  purpose: Option[String] = None,
  userId: Option[String] = None,
  cartItems: Option[String] = None,
  shippingAddress: Option[String] = None,
  billingAddress: Option[String] = None,
  deviceFingerprint: Option[String] = None,
  sessionDurationSec: Option[Int] = None,
  paymentMethod: Option[String] = None,
  deviceId: Option[String] = None,
  ipAddress: Option[String] = None
)
