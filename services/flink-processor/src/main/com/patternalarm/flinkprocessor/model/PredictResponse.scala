package com.patternalarm.flinkprocessor.model

/**
 * Response from FastAPI POST /predict
 * Matches FastAPI PredictResponse model exactly
 */
case class PredictResponse(
    fraudScore: Int,
    modelVersion: String,
    inferenceTimeMs: Int,
    transactionsAnalyzed: Int
  )

object PredictResponse {
  /**
   * Determine severity based on fraud score
   */
  def severity(fraudScore: Int): String = fraudScore match {
    case s if s >= 90 => "CRITICAL"
    case s if s >= 75 => "HIGH"
    case s if s >= 60 => "MEDIUM"
    case _ => "LOW"
  }
}