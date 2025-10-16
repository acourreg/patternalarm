package com.patternalarm.flinkprocessor.model

import java.time.Instant

/**
 * Request payload for FastAPI POST /predict
 * Uses TransactionEvent directly (FastAPI will ignore extra fields)
 */
case class PredictRequest(
   actorId: String,
   domain: String,
   transactionCount: Int,
   totalAmount: Double,
   timeDeltaSec: Long,
   windowStart: Instant,
   windowEnd: Instant,
   transactions: List[TransactionEvent]
 )

object PredictRequest {
  /**
   * Convert TimeWindowAggregate to PredictRequest
   */
  def fromAggregate(agg: TimeWindowAggregate): PredictRequest = {
    val timeDelta = java.time.Duration.between(agg.windowStart, agg.windowEnd).getSeconds

    PredictRequest(
      actorId = agg.actorId,
      domain = agg.domain,
      transactionCount = agg.transactionCount,
      totalAmount = agg.totalAmount,
      timeDeltaSec = timeDelta,
      windowStart = agg.windowStart,
      windowEnd = agg.windowEnd,
      transactions = agg.transactions
    )
  }
}