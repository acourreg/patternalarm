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

    AsyncDataStream.unorderedWait(
      aggregates,
      fraudScoringAsyncFunction,
      Config.FastApi.timeoutMs,
      TimeUnit.MILLISECONDS,
      Config.FastApi.maxConcurrentRequests
    ).asInstanceOf[DataStream[(TimedWindowAggregate, PredictResponse)]]
      .map(new ScoreLogger)
      .filter(new HighRiskFilter)
      .map(new AlertBuilder)
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

    // ✅ Register custom Kryo serializers for Java 9+ compatibility
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

  private[flinkprocessor] def buildAlert(aggregate: TimedWindowAggregate, response: PredictResponse): Alert =
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
class AlertLogger extends MapFunction[Alert, Alert] {
  override def map(alert: Alert): Alert = {
    println(s"🚨 ALERT: ${alert.severity} - actor=${alert.actorId}, score=${alert.fraudScore}, type=${alert.alertType}")
    alert
  }
}

@SerialVersionUID(104L)
class HighRiskFilter extends FilterFunction[(TimedWindowAggregate, PredictResponse)] {
  override def filter(tuple: (TimedWindowAggregate, PredictResponse)): Boolean =
    StreamProcessorJob.isHighRisk(tuple)
}

@SerialVersionUID(105L)
class AlertBuilder extends MapFunction[(TimedWindowAggregate, PredictResponse), Alert] {
  override def map(tuple: (TimedWindowAggregate, PredictResponse)): Alert = {
    val (aggregate, response) = tuple
    StreamProcessorJob.buildAlert(aggregate, response)
  }
}
