package com.patternalarm.flinkprocessor

import com.patternalarm.flinkprocessor.config.Config
import com.patternalarm.flinkprocessor.model._
import com.patternalarm.flinkprocessor.processor._
import com.patternalarm.flinkprocessor.serialization.InstantSerializer
import com.patternalarm.flinkprocessor.sink.FraudAlertSink
import com.patternalarm.flinkprocessor.utils.JsonUtils
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.{FilterFunction, MapFunction}
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.api.datastream.{AsyncDataStream, DataStream}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer

import java.time.{Duration, Instant}
import java.util.Properties
import java.util.concurrent.TimeUnit

class StreamProcessorJob(
  envProvider: () => StreamExecutionEnvironment,
  kafkaSourceProvider: StreamExecutionEnvironment => DataStream[String],
  fraudScoringAsyncFunction: FraudScoringAsyncFunction,
  alertSink: FraudAlertSink
) {

  def this() = this(
    StreamProcessorJob.setupEnvironment,
    StreamProcessorJob.createKafkaSource,
    new FraudScoringAsyncFunction(Config.FastApi.url),
    StreamProcessorJob.createAlertSink()
  )

  def run(args: Array[String] = Array.empty): Unit = {
    Config.printSummary()

    val env = envProvider()

    val aggregates = kafkaSourceProvider(env)
      .map(new TransactionJsonParser)
      .filter(_ != null)
      .assignTimestampsAndWatermarks(createWatermarkStrategy)
      .keyBy((event: TransactionEvent) => event.actorId)
      .window(TumblingEventTimeWindows.of(Time.minutes(Config.Flink.Windowing.sizeMinutes)))
      .apply(new TransactionWindowFunction())
      .map(new AggregateLogger)

    // ✅ Changed to (TimedWindowAggregate, PredictResponse, Seq[TransactionEvent])
    AsyncDataStream.unorderedWait(
      aggregates,
      fraudScoringAsyncFunction,
      Config.FastApi.timeoutMs,
      TimeUnit.MILLISECONDS,
      Config.FastApi.maxConcurrentRequests
    ).asInstanceOf[DataStream[(TimedWindowAggregate, PredictResponse)]]
      .map(new ScoreLogger)
      .filter(new HighRiskFilter)
      .map(new AlertWithTransactionsBuilder) // ✅ Builds (Alert, Seq[TransactionEvent])
      .map(new AlertLogger)
      .addSink(alertSink)

    println("✅ Starting Flink job execution...")
    env.execute("PatternAlarm Fraud Detection Pipeline")
  }

  private def createWatermarkStrategy: WatermarkStrategy[TransactionEvent] =
    WatermarkStrategy
      .forBoundedOutOfOrderness[TransactionEvent](
        Duration.ofSeconds(Config.Flink.Windowing.latenessSeconds)
      )
      .withTimestampAssigner(new SerializableTimestampAssigner[TransactionEvent] {
        override def extractTimestamp(event: TransactionEvent, recordTimestamp: Long): Long =
          event.timestamp.toEpochMilli
      })
}

object StreamProcessorJob {

  def main(args: Array[String]): Unit = {
    println("🚀 Starting PatternAlarm Fraud Detection Pipeline...")
    new StreamProcessorJob().run(args)
  }

  // ========== Factory Methods ==========

  def setupEnvironment(): StreamExecutionEnvironment = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.enableCheckpointing(Config.Flink.checkpointingIntervalMs)

    env.getConfig.registerTypeWithKryoSerializer(
      classOf[Instant],
      classOf[InstantSerializer]
    )

    env
  }

  def createKafkaSource(env: StreamExecutionEnvironment): DataStream[String] = {
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

  def createAlertSink(): FraudAlertSink =
    new FraudAlertSink(
      Config.Database.url,
      Config.Database.user,
      Config.Database.password
    )

  // ========== Domain Logic ==========

  private[flinkprocessor] def parseJson(json: String): TransactionEvent =
    try JsonUtils.fromJson[TransactionEvent](json)
    catch {
      case _: Exception => null
    }

  private[flinkprocessor] def isHighRisk(tuple: (TimedWindowAggregate, PredictResponse)): Boolean = {
    val (_, response) = tuple
    response.fraudScore >= Config.Flink.FraudDetection.scoreThreshold
  }

  /**
   * Builds an Alert with transactions from windowed aggregate and ML response
   * Returns: (Alert, Seq[TransactionEvent])
   */
  /**
   * Builds an Alert with transactions from windowed aggregate and ML response
   * Returns: (Alert, Seq[TransactionEvent])
   */
  private[flinkprocessor] def buildAlertWithTransactions(
    aggregate: TimedWindowAggregate,
    response: PredictResponse
  ): (Alert, Seq[TransactionEvent]) = {

    val patterns = aggregate.transactions
      .filter(_.isFraud)
      .map(_.pattern)
      .distinct
      .filter(_ != "regular")

    val firstTx = aggregate.transactions.headOption
    val windowSeconds = Duration.between(aggregate.windowStart, aggregate.windowEnd).getSeconds

    val alert = Alert(
      alertId = 0, // Auto-generated by DB
      alertType = determineAlertType(aggregate),
      domain = aggregate.domain,
      actorId = aggregate.actorId,
      severity = determineSeverity(response.fraudScore),
      fraudScore = response.fraudScore,
      transactionCount = aggregate.transactionCount,
      totalAmount = aggregate.totalAmount,
      firstSeen = aggregate.windowStart, // ✅ Instant (not Long)
      lastSeen = aggregate.windowEnd, // ✅ Instant (not Long)

      // ================================================
      // ML Metadata
      // ================================================
      windowSeconds = Some(windowSeconds),
      baselineAvg = Some(aggregate.totalAmount / aggregate.transactionCount),
      patternsDetected = Some(patterns),
      confidence = Some(response.fraudScore),
      modelVersion = Some(response.modelVersion),
      inferenceTimeMs = Some(response.inferenceTimeMs),

      // ================================================
      // Gaming Domain Fields
      // ================================================
      playerId = firstTx.flatMap(_.playerId),
      gameId = firstTx.flatMap(_.gameId),
      itemType = firstTx.flatMap(_.itemType),
      itemName = firstTx.flatMap(_.itemName),
      sessionLengthSec = firstTx.flatMap(_.sessionLengthSec),

      // ================================================
      // Fintech Domain Fields
      // ================================================
      accountFrom = firstTx.flatMap(_.accountFrom),
      accountTo = firstTx.flatMap(_.accountTo),
      transferType = firstTx.flatMap(_.transferType),
      countryFrom = firstTx.flatMap(_.countryFrom),
      countryTo = firstTx.flatMap(_.countryTo),
      purpose = firstTx.flatMap(_.purpose),

      // ================================================
      // Ecommerce Domain Fields
      // ================================================
      userId = firstTx.flatMap(_.userId),
      cartItems = firstTx.flatMap(_.cartItems),
      shippingAddress = firstTx.flatMap(_.shippingAddress),
      billingAddress = firstTx.flatMap(_.billingAddress),
      deviceFingerprint = firstTx.flatMap(_.deviceFingerprint),
      sessionDurationSec = firstTx.flatMap(_.sessionDurationSec),

      // ================================================
      // Cross-Domain Fields
      // ================================================
      paymentMethod = firstTx.flatMap(_.paymentMethod),
      deviceId = firstTx.flatMap(_.deviceId),
      ipAddress = Some(firstTx.map(_.ipAddress).getOrElse("unknown"))
    )

    (alert, aggregate.transactions)
  }

  private[flinkprocessor] def determineAlertType(aggregate: TimedWindowAggregate): String = {
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

  private[flinkprocessor] def determineSeverity(fraudScore: Int): String = fraudScore match {
    case s if s >= 90 => "CRITICAL"
    case s if s >= 75 => "HIGH"
    case s if s >= 60 => "MEDIUM"
    case _ => "LOW"
  }
}

// ========== Serializable Operators ==========

@SerialVersionUID(100L)
class TransactionJsonParser extends MapFunction[String, TransactionEvent] {
  override def map(json: String): TransactionEvent = StreamProcessorJob.parseJson(json)
}

@SerialVersionUID(101L)
class AggregateLogger extends MapFunction[TimedWindowAggregate, TimedWindowAggregate] {
  override def map(agg: TimedWindowAggregate): TimedWindowAggregate = {
    println(s"📊 Window: actor=${agg.actorId}, txns=${agg.transactionCount}, amount=${agg.totalAmount}")
    agg
  }
}

@SerialVersionUID(102L)
class ScoreLogger
    extends MapFunction[(TimedWindowAggregate, PredictResponse), (TimedWindowAggregate, PredictResponse)] {
  override def map(tuple: (TimedWindowAggregate, PredictResponse)): (TimedWindowAggregate, PredictResponse) = {
    val (agg, response) = tuple
    println(s"🎯 Score: actor=${agg.actorId}, fraud_score=${response.fraudScore}, model=${response.modelVersion}")
    tuple
  }
}

@SerialVersionUID(103L)
class AlertLogger extends MapFunction[(Alert, Seq[TransactionEvent]), (Alert, Seq[TransactionEvent])] {
  override def map(tuple: (Alert, Seq[TransactionEvent])): (Alert, Seq[TransactionEvent]) = {
    val (alert, _) = tuple
    println(s"🚨 ALERT: ${alert.severity} - actor=${alert.actorId}, score=${alert.fraudScore}, type=${alert.alertType}")
    tuple
  }
}

@SerialVersionUID(104L)
class HighRiskFilter extends FilterFunction[(TimedWindowAggregate, PredictResponse)] {
  override def filter(tuple: (TimedWindowAggregate, PredictResponse)): Boolean =
    StreamProcessorJob.isHighRisk(tuple)
}

@SerialVersionUID(105L)
class AlertWithTransactionsBuilder
    extends MapFunction[(TimedWindowAggregate, PredictResponse), (Alert, Seq[TransactionEvent])] {
  override def map(tuple: (TimedWindowAggregate, PredictResponse)): (Alert, Seq[TransactionEvent]) = {
    val (aggregate, response) = tuple
    StreamProcessorJob.buildAlertWithTransactions(aggregate, response)
  }
}
