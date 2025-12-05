package com.patternalarm.flinkprocessor.model

case class BatchPredictResponse(
                                 predictions: Seq[BatchPredictionResult],
                                 mlVersion: String,
                                 totalInferenceTimeMs: Double,
                                 actorsAnalyzed: Int
                               )

