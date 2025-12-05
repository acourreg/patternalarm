package com.patternalarm.flinkprocessor.processor

import com.patternalarm.flinkprocessor.model._
import com.patternalarm.flinkprocessor.utils.JsonUtils
import org.apache.flink.api.common.state.{ListState, ListStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.state.FunctionInitializationContext
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.util.Collector
import sttp.client3._

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

class FraudScoringBatchFunction(
                                 fastapiUrl: String,
                                 batchSize: Int = 100,
                                 flushIntervalMs: Long = 5000
                               ) extends ProcessFunction[TimedWindowAggregate, (TimedWindowAggregate, PredictResponse)]
  with CheckpointedFunction {

  @transient private var buffer: ArrayBuffer[TimedWindowAggregate] = _
  @transient private var backend: SttpBackend[Identity, Any] = _
  @transient private var lastFlushTime: Long = _

  // For checkpointing
  @transient private var checkpointedState: ListState[TimedWindowAggregate] = _

  override def open(parameters: Configuration): Unit = {
    buffer = ArrayBuffer.empty
    backend = HttpClientSyncBackend()
    lastFlushTime = System.currentTimeMillis()
    println(s"✅ FraudScoringBatchFunction initialized (batchSize=$batchSize, flushInterval=${flushIntervalMs}ms)")
  }

  override def close(): Unit = {
    if (backend != null) backend.close()
  }

  override def processElement(
                               aggregate: TimedWindowAggregate,
                               ctx: ProcessFunction[TimedWindowAggregate, (TimedWindowAggregate, PredictResponse)]#Context,
                               out: Collector[(TimedWindowAggregate, PredictResponse)]
                             ): Unit = {

    buffer += aggregate

    val now = System.currentTimeMillis()
    val shouldFlush = buffer.size >= batchSize || (now - lastFlushTime >= flushIntervalMs && buffer.nonEmpty)

    if (shouldFlush) {
      flushBuffer(out)
    }
  }

  private def flushBuffer(out: Collector[(TimedWindowAggregate, PredictResponse)]): Unit = {
    if (buffer.isEmpty) return

    val batchStart = System.currentTimeMillis()
    val currentBatch = buffer.toList
    buffer.clear()
    lastFlushTime = batchStart

    println(s"📤 BATCH size=${currentBatch.size} actors=${currentBatch.map(_.actorId).mkString(",")}")

    try {
      // Build batch request
      val predictions = currentBatch.map { agg =>
        PredictRequest.fromAggregate(agg)
      }
      val batchRequest = BatchPredictRequest(predictions)
      val requestJson = JsonUtils.toJson(batchRequest)

      // Sync HTTP call
      val response = basicRequest
        .post(uri"$fastapiUrl/predict/batch")
        .contentType("application/json")
        .body(requestJson)
        .readTimeout(scala.concurrent.duration.Duration(30, "seconds"))
        .response(asStringAlways)
        .send(backend)

      val elapsed = System.currentTimeMillis() - batchStart

      if (response.code.isSuccess) {
        val batchResponse = JsonUtils.fromJson[BatchPredictResponse](response.body)

        println(s"🎯 BATCH OK size=${batchResponse.predictions.size} time=${batchResponse.totalInferenceTimeMs}ms elapsed=${elapsed}ms")

        // Match responses to aggregates by actor_id
        val responseMap = batchResponse.predictions.map(p => p.actorId -> p).toMap

        currentBatch.foreach { agg =>
          responseMap.get(agg.actorId) match {
            case Some(pred) =>
              println(s"🎯 actor=${agg.actorId} score=${(pred.confidence * 100).toInt} fraud=${pred.isFraud}")
              val predictResponse = PredictResponse(
                fraudScore = (pred.confidence * 100).toInt,
                modelVersion = pred.mlVersion,
                inferenceTimeMs = pred.inferenceTimeMs.toInt,
                transactionsAnalyzed = pred.transactionsAnalyzed
              )

              out.collect((agg, predictResponse))

            case None =>
              System.err.println(s"⚠️ No response for actor=${agg.actorId}")
              // Emit with zero score as fallback
              out.collect((agg, PredictResponse(0, "error", 0, 0)))
          }
        }
      } else {
        System.err.println(s"❌ BATCH API error code=${response.code} body=${response.body.take(500)}")
        // Emit fallback for all
        currentBatch.foreach { agg =>
          out.collect((agg, PredictResponse(0, "error", 0, 0)))
        }
      }
    } catch {
      case e: Exception =>
        val elapsed = System.currentTimeMillis() - batchStart
        System.err.println(s"❌ BATCH HTTP failed: ${e.getMessage} elapsed=${elapsed}ms")
        e.printStackTrace()
        // Emit fallback for all
        currentBatch.foreach { agg =>
          out.collect((agg, PredictResponse(0, "error", 0, 0)))
        }
    }
  }

  // Checkpoint support - save buffer state
  override def snapshotState(context: org.apache.flink.runtime.state.FunctionSnapshotContext): Unit = {
    checkpointedState.clear()
    buffer.foreach(agg => checkpointedState.add(agg))
  }

  override def initializeState(context: FunctionInitializationContext): Unit = {
    val descriptor = new ListStateDescriptor[TimedWindowAggregate](
      "buffered-aggregates",
      classOf[TimedWindowAggregate]
    )
    checkpointedState = context.getOperatorStateStore.getListState(descriptor)

    if (context.isRestored) {
      buffer = ArrayBuffer.empty
      checkpointedState.get().asScala.foreach(buffer += _)
      println(s"🔄 Restored ${buffer.size} buffered aggregates from checkpoint")
    }
  }
}