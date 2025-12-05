package com.patternalarm.flinkprocessor.model

case class BatchPredictionResult(
                                  actorId: String,
                                  fraudType: String,
                                  isFraud: Boolean,
                                  confidence: Double,
                                  transactionsAnalyzed: Int,
                                  totalAmount: Double,
                                  timeWindowSec: Double,
                                  mlVersion: String,
                                  inferenceTimeMs: Double
                                )
