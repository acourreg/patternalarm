package com.patternalarm.flinkprocessor.model

import java.time.Instant

/**
 * Aggregated result from 1-minute tumbling window
 * Pure data container - no business logic
 *
 * Used to aggregate transactions by actor_id before calling /predict
 */
case class TimedWindowAggregate(
  actorId: String,
  domain: String,

  // Aggregated metrics
  transactionCount: Int,
  totalAmount: Double,

  // Window boundaries
  windowStart: Instant,
  windowEnd: Instant,

  // Raw transactions in this window (sent to FastAPI for ML scoring)
  transactions: List[TransactionEvent]
)