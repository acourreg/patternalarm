package com.patternalarm.flinkprocessor.model

import java.time.Instant

/**
 * AlertDetail - Combines alert summary with individual transactions
 * This is what gets stored in the database (not sent over Kafka)
 */
case class AlertDetail(
  // Alert summary fields
  alertId: Long,
  alertType: String,
  domain: String,
  actorId: String,
  severity: String,
  fraudScore: Int,
  transactionCount: Int,
  totalAmount: Double,
  firstSeen: Instant,
  lastSeen: Instant,

  // Nested transactions
  transactions: Seq[TransactionEvent],

  // ML metadata
  modelVersion: String,
  inferenceTimeMs: Int
)
