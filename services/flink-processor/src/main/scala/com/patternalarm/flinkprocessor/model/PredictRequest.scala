package com.patternalarm.flinkprocessor.model

/**
 * Request to FastAPI ML service for fraud prediction
 */
case class PredictRequest(
  actorId: String,
  domain: String,
  transactionCount: Int,
  totalAmount: Double,
  avgAmount: Double,
  timeWindowSeconds: Long,
  transactions: Seq[TransactionEvent]
)

object PredictRequest {
  def fromAggregate(agg: TimedWindowAggregate): PredictRequest = {
    val timeWindowSeconds = java.time.Duration.between(agg.windowStart, agg.windowEnd).getSeconds

    PredictRequest(
      actorId = agg.actorId,
      domain = agg.domain,
      transactionCount = agg.transactionCount,
      totalAmount = agg.totalAmount,
      avgAmount = if (agg.transactionCount > 0) agg.totalAmount / agg.transactionCount else 0.0,
      timeWindowSeconds = timeWindowSeconds,
      transactions = agg.transactions
    )
  }
}
