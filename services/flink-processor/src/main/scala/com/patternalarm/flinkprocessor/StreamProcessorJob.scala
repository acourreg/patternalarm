package com.patternalarm.flinkprocessor

import com.patternalarm.flinkprocessor.HighRiskFilter.passed
import com.patternalarm.flinkprocessor.config.Config
import com.patternalarm.flinkprocessor.model._
import com.patternalarm.flinkprocessor.processor._
import com.patternalarm.flinkprocessor.serialization.InstantSerializer
import com.patternalarm.flinkprocessor.sink.FraudAlertSink
import com.patternalarm.flinkprocessor.utils.JsonUtils
import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.common.functions.{FilterFunction, MapFunction}
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer

import java.time.{Duration, Instant}
import java.util.Properties

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
    val kafkaSource = kafkaSourceProvider(env)

    val parsedStream = kafkaSource.map(new TransactionJsonParser)
    val filteredStream = parsedStream.filter(_ != null)
    val watermarkedStream = filteredStream.assignTimestampsAndWatermarks(createWatermarkStrategy)
    val keyedStream = watermarkedStream.keyBy((event: TransactionEvent) => event.actorId)
    val windowedStream = keyedStream.window(TumblingEventTimeWindows.of(Time.minutes(Config.Flink.Windowing.sizeMinutes)))
    val aggregates = windowedStream.apply(new TransactionWindowFunction()).map(new AggregateLogger)

    val scoredStream = aggregates
      .process(new FraudScoringBatchFunction(
        Config.FastApi.url,
        batchSize = 100,
        flushIntervalMs = 5000
      ))

    scoredStream
      .map(new ScoreLogger)
      .filter(new HighRiskFilter)
      .map(new AlertWithTransactionsBuilder)
      .map(new AlertLogger)
      .addSink(alertSink)

    try {
      env.execute("PatternAlarm Fraud Detection Pipeline")
    } catch {
      case e: Exception =>
        System.err.println(s"❌ ERROR: Flink job failed! ${e.getClass.getName}: ${e.getMessage}")
        e.printStackTrace()
        throw e
    }
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
    startHealthCheck()

    try {
      new StreamProcessorJob().run(args)
    } catch {
      case e: Exception =>
        System.err.println(s"❌ FATAL: ${e.getClass.getName}: ${e.getMessage}")
        e.printStackTrace()
        System.exit(1)
    }
  }

  def setupEnvironment(): StreamExecutionEnvironment = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.enableCheckpointing(Config.Flink.checkpointingIntervalMs)
    env.getConfig.registerTypeWithKryoSerializer(classOf[Instant], classOf[InstantSerializer])
    env
  }

  def createKafkaSource(env: StreamExecutionEnvironment): DataStream[String] = {
    val kafkaProperties = new Properties()
    kafkaProperties.setProperty("bootstrap.servers", Config.Kafka.bootstrapServers)
    kafkaProperties.setProperty("group.id", Config.Kafka.groupId)
    kafkaProperties.setProperty("auto.offset.reset", Config.Kafka.autoOffsetReset)
    kafkaProperties.setProperty("session.timeout.ms", "30000")
    kafkaProperties.setProperty("request.timeout.ms", "40000")
    kafkaProperties.setProperty("metadata.max.age.ms", "30000")

    try {
      val consumer = new FlinkKafkaConsumer[String](Config.Kafka.topic, new SimpleStringSchema(), kafkaProperties)
      env.addSource(consumer)
    } catch {
      case e: Exception =>
        System.err.println(s"❌ Kafka source failed: ${e.getMessage}")
        throw e
    }
  }

  def createAlertSink(): FraudAlertSink = {
    try {
      new FraudAlertSink(Config.Database.url, Config.Database.user, Config.Database.password)
    } catch {
      case e: Exception =>
        System.err.println(s"❌ FraudAlertSink failed: ${e.getMessage}")
        throw e
    }
  }

  def startHealthCheck(): Unit = {
    val healthCheckThread = new Thread(() => {
      try {
        val serverSocket = new java.net.ServerSocket(8081)
        while (true) {
          val socket = serverSocket.accept()
          val out = new java.io.PrintWriter(socket.getOutputStream, true)
          out.println("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\n\r\nOK")
          out.flush()
          socket.close()
        }
      } catch {
        case e: Exception => System.err.println(s"❌ Health check error: ${e.getMessage}")
      }
    })
    healthCheckThread.setDaemon(true)
    healthCheckThread.start()
  }

  private[flinkprocessor] def parseJson(json: String): TransactionEvent =
    try {
      JsonUtils.fromJson[TransactionEvent](json)
    } catch {
      case e: Exception =>
        System.err.println(s"⚠️ JSON parse failed: ${e.getMessage}")
        null
    }

  private[flinkprocessor] def isHighRisk(tuple: (TimedWindowAggregate, PredictResponse)): Boolean = {
    val (_, response) = tuple
    response.fraudScore >= Config.Flink.FraudDetection.scoreThreshold
  }

  private[flinkprocessor] def buildAlertWithTransactions(
                                                          aggregate: TimedWindowAggregate,
                                                          response: PredictResponse
                                                        ): (Alert, Seq[TransactionEvent]) = {

    val patterns = aggregate.transactions.filter(_.isFraud).map(_.pattern).distinct.filter(_ != "regular")
    val firstTx = aggregate.transactions.headOption
    val windowSeconds = Duration.between(aggregate.windowStart, aggregate.windowEnd).getSeconds

    val alert = Alert(
      alertId = 0,
      alertType = determineAlertType(aggregate),
      domain = aggregate.domain,
      actorId = aggregate.actorId,
      severity = determineSeverity(response.fraudScore),
      fraudScore = response.fraudScore,
      transactionCount = aggregate.transactionCount,
      totalAmount = aggregate.totalAmount,
      firstSeen = aggregate.windowStart,
      lastSeen = aggregate.windowEnd,
      windowSeconds = Some(windowSeconds),
      baselineAvg = Some(aggregate.totalAmount / aggregate.transactionCount),
      patternsDetected = Some(patterns),
      confidence = Some(response.fraudScore),
      modelVersion = Some(response.modelVersion),
      inferenceTimeMs = Some(response.inferenceTimeMs),
      playerId = firstTx.flatMap(_.playerId),
      gameId = firstTx.flatMap(_.gameId),
      itemType = firstTx.flatMap(_.itemType),
      itemName = firstTx.flatMap(_.itemName),
      sessionLengthSec = firstTx.flatMap(_.sessionLengthSec),
      accountFrom = firstTx.flatMap(_.accountFrom),
      accountTo = firstTx.flatMap(_.accountTo),
      transferType = firstTx.flatMap(_.transferType),
      countryFrom = firstTx.flatMap(_.countryFrom),
      countryTo = firstTx.flatMap(_.countryTo),
      purpose = firstTx.flatMap(_.purpose),
      userId = firstTx.flatMap(_.userId),
      cartItems = firstTx.flatMap(_.cartItems),
      shippingAddress = firstTx.flatMap(_.shippingAddress),
      billingAddress = firstTx.flatMap(_.billingAddress),
      deviceFingerprint = firstTx.flatMap(_.deviceFingerprint),
      sessionDurationSec = firstTx.flatMap(_.sessionDurationSec),
      paymentMethod = firstTx.flatMap(_.paymentMethod),
      deviceId = firstTx.flatMap(_.deviceId),
      ipAddress = Some(firstTx.map(_.ipAddress).getOrElse("unknown"))
    )

    (alert, aggregate.transactions)
  }

  private[flinkprocessor] def determineAlertType(aggregate: TimedWindowAggregate): String = {
    val fraudPatterns = aggregate.transactions.filter(_.isFraud).map(_.pattern)
    if (fraudPatterns.isEmpty) "suspicious_activity"
    else fraudPatterns.groupBy(identity).maxBy(_._2.size)._1.replace("fraud_", "")
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
  override def map(agg: TimedWindowAggregate): TimedWindowAggregate = agg
}

@SerialVersionUID(102L)
class ScoreLogger extends MapFunction[(TimedWindowAggregate, PredictResponse), (TimedWindowAggregate, PredictResponse)] {
  override def map(tuple: (TimedWindowAggregate, PredictResponse)): (TimedWindowAggregate, PredictResponse) = {
    ScoreLogger.count += 1
    val (agg, response) = tuple

    // Track high risk separately
    if (response.fraudScore >= 60) {
      ScoreLogger.highRiskCount += 1
    }

    // Stats every 50 scores (not 100)
    if (ScoreLogger.count % 50 == 0) {
      val elapsed = (System.currentTimeMillis() - ScoreLogger.startTime) / 1000
      val rate = if (elapsed > 0) ScoreLogger.count / elapsed else 0
      println(s"📊 SCORING: total=${ScoreLogger.count} | high_risk=${ScoreLogger.highRiskCount} | ${rate}/s | ${elapsed}s elapsed")
    }

    // Only log individual scores for HIGH risk (score >= 75)
    if (response.fraudScore >= 75) {
      println(s"🎯 HIGH_RISK: actor=${agg.actorId} score=${response.fraudScore} txns=${agg.transactionCount}")
    }

    tuple
  }
}

object ScoreLogger {
  var count = 0L
  var highRiskCount = 0L
  val startTime: Long = System.currentTimeMillis()
}

@SerialVersionUID(103L)
class AlertLogger extends MapFunction[(Alert, Seq[TransactionEvent]), (Alert, Seq[TransactionEvent])] {
  override def map(tuple: (Alert, Seq[TransactionEvent])): (Alert, Seq[TransactionEvent]) = tuple
}

@SerialVersionUID(104L)
class HighRiskFilter extends FilterFunction[(TimedWindowAggregate, PredictResponse)] {
  override def filter(tuple: (TimedWindowAggregate, PredictResponse)): Boolean = {
    val passed = StreamProcessorJob.isHighRisk(tuple)
    HighRiskFilter.total += 1
    if (passed) HighRiskFilter.passed += 1

    // Log filter stats every 100
    if (HighRiskFilter.total % 100 == 0) {
      val rate = (HighRiskFilter.passed.toDouble / HighRiskFilter.total * 100).toInt
      println(s"🔍 FILTER: ${HighRiskFilter.passed}/${HighRiskFilter.total} passed ($rate%)")
    }

    passed
  }
}

object HighRiskFilter {
  var total = 0L
  var passed = 0L
}

@SerialVersionUID(105L)
class AlertWithTransactionsBuilder extends MapFunction[(TimedWindowAggregate, PredictResponse), (Alert, Seq[TransactionEvent])] {
  override def map(tuple: (TimedWindowAggregate, PredictResponse)): (Alert, Seq[TransactionEvent]) = {
    val (aggregate, response) = tuple
    StreamProcessorJob.buildAlertWithTransactions(aggregate, response)
  }
}

