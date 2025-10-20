package com.patternalarm.flinkprocessor.model

import java.time.Instant

/**
 * Aggregated window result from Flink
 * Contains transactions grouped by actor within a time window
 */
case class TimedWindowAggregate(
  actorId: String,
  domain: String,
  transactionCount: Int,
  totalAmount: Double,
  windowStart: Instant,
  windowEnd: Instant,
  transactions: Seq[TransactionEvent]
)
