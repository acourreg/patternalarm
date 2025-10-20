/** MACHINE-GENERATED FROM AVRO SCHEMA. DO NOT EDIT DIRECTLY */
package com.patternalarm.flinkprocessor.model

/**
 * Raw transaction event from Kafka. Supports all 3 domains: gaming, fintech, ecommerce
 * @param transactionId
 * @param domain
 * @param testId
 * @param timestamp
 * @param actorId
 * @param amount
 * @param currency
 * @param ipAddress
 * @param pattern
 * @param isFraud
 * @param sequencePosition
 * @param playerId
 * @param gameId
 * @param itemType
 * @param itemName
 * @param paymentMethod
 * @param deviceId
 * @param sessionLengthSec
 * @param accountFrom
 * @param accountTo
 * @param transferType
 * @param countryFrom
 * @param countryTo
 * @param purpose
 * @param userId
 * @param cartItems
 * @param shippingAddress
 * @param billingAddress
 * @param deviceFingerprint
 * @param sessionDurationSec
 */
final case class TransactionEvent(
  transactionId: String,
  domain: String,
  testId: String,
  timestamp: java.time.Instant,
  actorId: String,
  amount: Double,
  currency: String,
  ipAddress: String,
  pattern: String,
  isFraud: Boolean,
  sequencePosition: Int,
  playerId: Option[String] = None,
  gameId: Option[String] = None,
  itemType: Option[String] = None,
  itemName: Option[String] = None,
  paymentMethod: Option[String] = None,
  deviceId: Option[String] = None,
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
  sessionDurationSec: Option[Int] = None
)
