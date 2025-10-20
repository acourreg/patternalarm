package com.patternalarm.flinkprocessor.model

/**
 * Response from FastAPI ML service
 */
case class PredictResponse(
  fraudScore: Int,
  modelVersion: String,
  inferenceTimeMs: Int,
  transactionsAnalyzed: Int
)
