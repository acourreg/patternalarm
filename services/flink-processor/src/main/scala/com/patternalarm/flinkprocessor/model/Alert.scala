/** MACHINE-GENERATED FROM AVRO SCHEMA. DO NOT EDIT DIRECTLY */
package com.patternalarm.flinkprocessor.model

/**
 * Alert summary (fraud_alerts table)
 * @param alertId
 * @param alertType
 * @param domain
 * @param actorId
 * @param severity
 * @param fraudScore
 * @param transactionCount
 * @param totalAmount
 * @param firstSeen
 * @param lastSeen
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
  lastSeen: java.time.Instant
)
