/** MACHINE-GENERATED FROM AVRO SCHEMA. DO NOT EDIT DIRECTLY */
package com.patternalarm.flinkprocessor.model

/**
 * Alert metadata structure
 * @param windowSeconds Window duration in seconds
 * @param baselineAvg Baseline average for comparison
 * @param patternsDetected List of fraud patterns detected
 * @param confidence Confidence score
 * @param modelVersion ML model version used
 * @param inferenceTimeMs ML inference time in milliseconds
 */
final case class AlertMetadata(
  windowSeconds: Long,
  baselineAvg: Double,
  patternsDetected: Seq[String],
  confidence: Int,
  modelVersion: String,
  inferenceTimeMs: Int
)
