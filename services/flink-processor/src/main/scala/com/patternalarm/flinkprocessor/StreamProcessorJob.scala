package com.patternalarm.flinkprocessor

import com.patternalarm.flinkprocessor.config.Config
import com.patternalarm.flinkprocessor.model._
import com.patternalarm.flinkprocessor.processor._
import com.patternalarm.flinkprocessor.sink.FraudAlertSink
import com.patternalarm.flinkprocessor.utils.JsonUtils
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.api.datastream.{AsyncDataStream, DataStream, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer

import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit

/**
 * PatternAlarm Fraud Detection Pipeline (Flink 1.18+)
 *
 * Flow:
 * 1. Kafka Source → Raw JSON
 * 2. Parse → TransactionEvent
 * 3. Assign Timestamps & Watermarks
 * 4. Window & Aggregate (1-minute tumbling)
 * 5. Async ML Scoring (FastAPI)
 * 6. Filter High-Risk (score >= 70)
 * 7. Build Alert
 * 8. Sink to PostgreSQL
 */
object StreamProcessorJob {

  def main(args: Array[String]): Unit = {
    Config.printSummary()

    val env = setupEnvironment()

    // Functional pipeline
    val rawStream = createKafkaSource(env)
    val transactions = parseTransactions(rawStream)
    val timestamped = assignTimestamps(transactions)
    val aggregates = windowAndAggregate(timestamped)
    val scored = asyncMLScoring(aggregates)
    val alerts = filterAndBuildAlerts(scored)
    sinkToDatabase(alerts)

    println("✅ Starting Flink job execution...")
    env.execute("PatternAlarm Fraud Detection Pipeline")
  }

  // ========== Pipeline Steps ==========

  /**
   * Step 0: Setup Flink execution environment
   */
  private def setupEnvironment(): StreamExecutionEnvironment = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.enableCheckpointing(Config.Flink.checkpointingIntervalMs)
    env
  }

  /**
   * Step 1: Create Kafka source
   */
  private def createKafkaSource(env: StreamExecutionEnvironment): DataStream[String] = {
    val kafkaProperties = new Properties()
    kafkaProperties.setProperty("bootstrap.servers", Config.Kafka.bootstrapServers)
    kafkaProperties.setProperty("group.id", Config.Kafka.groupId)
    kafkaProperties.setProperty("auto.offset.reset", Config.Kafka.autoOffsetReset)

    env.addSource(
      new FlinkKafkaConsumer[String](
        Config.Kafka.topic,
        new SimpleStringSchema(),
        kafkaProperties
      )
    )
  }

  /**
   * Step 2: Parse JSON to TransactionEvent
   */
  private def parseTransactions(rawStream: DataStream[String]): DataStream[TransactionEvent] =
    rawStream
      .map(parseJson(_))
      .filter(_ != null)

  private def parseJson(json: String): TransactionEvent =
    try
      JsonUtils.fromJson[TransactionEvent](json)
    catch {
      case e: Exception =>
        println(s"⚠️  Failed to parse JSON: ${e.getMessage}")
        null
    }

  /**
   * Step 3: Assign event timestamps and watermarks
   */
  private def assignTimestamps(stream: DataStream[TransactionEvent]): DataStream[TransactionEvent] =
    stream.assignTimestampsAndWatermarks(
      WatermarkStrategy
        .forBoundedOutOfOrderness[TransactionEvent](
          Duration.ofSeconds(Config.Flink.Windowing.latenessSeconds)
        )
        .withTimestampAssigner(new SerializableTimestampAssigner[TransactionEvent] {
          override def extractTimestamp(event: TransactionEvent, recordTimestamp: Long): Long =
            event.timestamp.toEpochMilli
        })
    )

  /**
   * Step 4: Window by actor and aggregate transactions
   */
  private def windowAndAggregate(stream: DataStream[TransactionEvent]): DataStream[TimedWindowAggregate] = {

    val aggregates = stream
      .keyBy((event: TransactionEvent) => event.actorId)
      .window(TumblingEventTimeWindows.of(Time.minutes(Config.Flink.Windowing.sizeMinutes)))
      .apply(new TransactionWindowFunction())

    // Debug output
    aggregates.map(formatAggregateLog(_)).print()

    aggregates
  }

  private def formatAggregateLog(agg: TimedWindowAggregate): String =
    s"📊 Window: actor=${agg.actorId}, txns=${agg.transactionCount}, amount=${agg.totalAmount}"

  /**
   * Step 5: Async ML scoring via FastAPI
   */
  private def asyncMLScoring(stream: DataStream[TimedWindowAggregate])
    : DataStream[(TimedWindowAggregate, PredictResponse)] = {
    val scored = AsyncDataStream.unorderedWait(
      stream,
      new FraudScoringAsyncFunction(Config.FastApi.url),
      Config.FastApi.timeoutMs,
      TimeUnit.MILLISECONDS,
      Config.FastApi.maxConcurrentRequests
    ).asInstanceOf[DataStream[(TimedWindowAggregate, PredictResponse)]]

    // Debug output
    scored.map(formatScoreLog(_)).print()

    scored
  }

  private def formatScoreLog(tuple: (TimedWindowAggregate, PredictResponse)): String = {
    val (agg, response) = tuple
    s"🎯 Score: actor=${agg.actorId}, fraud_score=${response.fraudScore}, model=${response.modelVersion}"
  }

  /**
   * Step 6: Filter high-risk and build alerts
   */
  private def filterAndBuildAlerts(stream: DataStream[(TimedWindowAggregate, PredictResponse)]): DataStream[Alert] = {
    val filtered: SingleOutputStreamOperator[(TimedWindowAggregate, PredictResponse)] = stream
      .filter(isHighRisk(_))

    val alerts: SingleOutputStreamOperator[Alert] = filtered
      .map { case (aggregate, response) => buildAlert(aggregate, response) }

    // Debug output
    alerts.map(formatAlertLog(_)).print()

    alerts // SingleOutputStreamOperator extends DataStream, donc OK
  }

  private def isHighRisk(tuple: (TimedWindowAggregate, PredictResponse)): Boolean = {
    val (_, response) = tuple
    response.fraudScore >= Config.Flink.FraudDetection.scoreThreshold
  }

  private def formatAlertLog(alert: Alert): String =
    s"🚨 ALERT: ${alert.severity} - actor=${alert.actorId}, score=${alert.fraudScore}, type=${alert.alertType}"

  /**
   * Step 7: Sink to PostgreSQL
   */
  private def sinkToDatabase(stream: DataStream[Alert]): Unit =
    stream.addSink(
      new FraudAlertSink(
        Config.Database.url,
        Config.Database.user,
        Config.Database.password
      )
    )

  // ========== Domain Logic ==========

  /**
   * Build Alert from aggregated window and ML response
   */
  private def buildAlert(aggregate: TimedWindowAggregate, response: PredictResponse): Alert =
    Alert(
      alertId = 0,
      alertType = determineAlertType(aggregate),
      domain = aggregate.domain,
      actorId = aggregate.actorId,
      severity = determineSeverity(response.fraudScore),
      fraudScore = response.fraudScore,
      transactionCount = aggregate.transactionCount,
      totalAmount = aggregate.totalAmount,
      firstSeen = aggregate.windowStart,
      lastSeen = aggregate.windowEnd
    )

  /**
   * Determine alert type from most common fraud pattern
   */
  private def determineAlertType(aggregate: TimedWindowAggregate): String = {
    val fraudPatterns = aggregate.transactions
      .filter(_.isFraud)
      .map(_.pattern)

    if (fraudPatterns.isEmpty) {
      "suspicious_activity"
    } else {
      fraudPatterns
        .groupBy(identity)
        .maxBy(_._2.size)
        ._1
        .replace("fraud_", "")
    }
  }

  /**
   * Determine severity level based on fraud score
   */
  private def determineSeverity(fraudScore: Int): String = fraudScore match {
    case s if s >= 90 => "CRITICAL"
    case s if s >= 75 => "HIGH"
    case s if s >= 60 => "MEDIUM"
    case _ => "LOW"
  }
}
