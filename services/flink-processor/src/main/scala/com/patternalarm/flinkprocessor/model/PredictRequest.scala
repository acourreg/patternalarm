package com.patternalarm.flinkprocessor.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Request to FastAPI ML service for fraud prediction
 */
case class PredictRequest(
                           @JsonProperty("actor_id") actorId: String,
                           domain: String,
                           @JsonProperty("transaction_count") transactionCount: Int,
                           @JsonProperty("total_amount") totalAmount: Double,
                           @JsonProperty("avg_amount") avgAmount: Double,
                           @JsonProperty("time_delta_sec") timeDeltaSec: Long,
                           @JsonProperty("window_start") windowStart: String,  // ISO format
                           @JsonProperty("window_end") windowEnd: String,      // ISO format
                           transactions: Seq[TransactionEvent]
                         )


object PredictRequest {
  def fromAggregate(agg: TimedWindowAggregate): PredictRequest = {
    val timeDeltaSec = java.time.Duration.between(agg.windowStart, agg.windowEnd).getSeconds

    PredictRequest(
      actorId = agg.actorId,
      domain = agg.domain,
      transactionCount = agg.transactionCount,
      totalAmount = agg.totalAmount,
      avgAmount = if (agg.transactionCount > 0) agg.totalAmount / agg.transactionCount else 0.0,
      timeDeltaSec = timeDeltaSec,
      windowStart = agg.windowStart.toString,  // ISO-8601 format
      windowEnd = agg.windowEnd.toString,
      transactions = agg.transactions
    )
  }
}
