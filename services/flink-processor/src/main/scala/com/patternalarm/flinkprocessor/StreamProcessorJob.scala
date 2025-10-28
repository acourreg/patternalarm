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
    println("📋 [STEP 1] Printing configuration summary...")
    Config.printSummary()

    println("📋 [STEP 2] Setting up Flink environment...")
    val env = envProvider()
    println("✅ Flink environment created successfully")

    println("📋 [STEP 3] Creating Kafka source...")
    val kafkaSource = kafkaSourceProvider(env)
    println("✅ Kafka source created successfully")

    println("📋 [STEP 4] Building processing pipeline...")
    
    println("  └─ Adding JSON parser...")
    val parsedStream = kafkaSource.map(new TransactionJsonParser)
    println("  └─ Adding null filter...")
    val filteredStream = parsedStream.filter(_ != null)
    println("  └─ Adding watermark strategy...")
    val watermarkedStream = filteredStream.assignTimestampsAndWatermarks(createWatermarkStrategy)
    println("  └─ Adding keyBy operation...")
    val keyedStream = watermarkedStream.keyBy((event: TransactionEvent) => event.actorId)
    println("  └─ Adding tumbling window...")
    val windowedStream = keyedStream.window(TumblingEventTimeWindows.of(Time.minutes(Config.Flink.Windowing.sizeMinutes)))
    println("  └─ Adding window function...")
    val aggregates = windowedStream.apply(new TransactionWindowFunction()).map(new AggregateLogger)
    println("✅ Windowing pipeline configured")

    println("📋 [STEP 5] Adding async fraud scoring...")
    val scoredStream = AsyncDataStream.unorderedWait(
      aggregates,
      fraudScoringAsyncFunction,
      Config.FastApi.timeoutMs,
      TimeUnit.MILLISECONDS,
      Config.FastApi.maxConcurrentRequests
    ).asInstanceOf[DataStream[(TimedWindowAggregate, PredictResponse)]]
    println("✅ Fraud scoring configured")

    println("📋 [STEP 6] Adding filtering and alerting...")
    scoredStream
      .map(new ScoreLogger)
      .filter(new HighRiskFilter)
      .map(new AlertWithTransactionsBuilder)
      .map(new AlertLogger)
      .addSink(alertSink)
    println("✅ Complete pipeline configured")

    println("✅ [STEP 7] Starting Flink job execution...")
    println("⏳ Waiting for data from Kafka topic: " + Config.Kafka.topic)
    println("⏳ Consumer group: " + Config.Kafka.groupId)
    
    try {
      env.execute("PatternAlarm Fraud Detection Pipeline")
      println("✅ Flink job completed successfully")
    } catch {
      case e: Exception =>
        System.err.println("❌ ERROR: Flink job failed!")
        System.err.println(s"❌ Exception: ${e.getClass.getName}")
        System.err.println(s"❌ Message: ${e.getMessage}")
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
    println("🔧 Initializing health check server...")
    startHealthCheck()
    println("🔧 Creating StreamProcessorJob instance...")
    
    try {
      new StreamProcessorJob().run(args)
    } catch {
      case e: Exception =>
        System.err.println("❌ FATAL ERROR in main!")
        System.err.println(s"❌ Exception: ${e.getClass.getName}")
        System.err.println(s"❌ Message: ${e.getMessage}")
        e.printStackTrace()
        System.exit(1)
    }
  }

  // ========== Factory Methods ==========

  def setupEnvironment(): StreamExecutionEnvironment = {
    println("🔧 Creating Flink execution environment...")
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    
    println(s"🔧 Enabling checkpointing: ${Config.Flink.checkpointingIntervalMs}ms")
    env.enableCheckpointing(Config.Flink.checkpointingIntervalMs)

    println("🔧 Registering Kryo serializer for Instant...")
    env.getConfig.registerTypeWithKryoSerializer(
      classOf[Instant],
      classOf[InstantSerializer]
    )

    println("✅ Flink environment setup complete")
    env
  }

  def createKafkaSource(env: StreamExecutionEnvironment): DataStream[String] = {
    println("🔌 Setting up Kafka consumer...")
    
    val kafkaProperties = new Properties()
    kafkaProperties.setProperty("bootstrap.servers", Config.Kafka.bootstrapServers)
    kafkaProperties.setProperty("group.id", Config.Kafka.groupId)
    kafkaProperties.setProperty("auto.offset.reset", Config.Kafka.autoOffsetReset)
    
    // Add timeouts to fail fast
    kafkaProperties.setProperty("session.timeout.ms", "30000")
    kafkaProperties.setProperty("request.timeout.ms", "40000")
    kafkaProperties.setProperty("metadata.max.age.ms", "30000")
    
    println(s"🔌 Kafka Bootstrap Servers: ${Config.Kafka.bootstrapServers}")
    println(s"🔌 Kafka Topic: ${Config.Kafka.topic}")
    println(s"🔌 Kafka Group ID: ${Config.Kafka.groupId}")
    println(s"🔌 Auto Offset Reset: ${Config.Kafka.autoOffsetReset}")

    try {
      println("🔌 Creating FlinkKafkaConsumer instance...")
      val consumer = new FlinkKafkaConsumer[String](
        Config.Kafka.topic,
        new SimpleStringSchema(),
        kafkaProperties
      )
      
      println("✅ FlinkKafkaConsumer created successfully")
      println("🔌 Adding Kafka source to environment...")
      
      val source = env.addSource(consumer)
      println("✅ Kafka source added to environment")
      
      source
    } catch {
      case e: Exception =>
        System.err.println("❌ ERROR: Failed to create Kafka source!")
        System.err.println(s"❌ Exception: ${e.getClass.getName}")
        System.err.println(s"❌ Message: ${e.getMessage}")
        e.printStackTrace()
        throw e
    }
  }

  def createAlertSink(): FraudAlertSink = {
    println("💾 Creating FraudAlertSink...")
    println(s"💾 Database URL: ${Config.Database.url}")
    println(s"💾 Database User: ${Config.Database.user}")
    
    try {
      val sink = new FraudAlertSink(
        Config.Database.url,
        Config.Database.user,
        Config.Database.password
      )
      println("✅ FraudAlertSink created successfully")
      sink
    } catch {
      case e: Exception =>
        System.err.println("❌ ERROR: Failed to create FraudAlertSink!")
        System.err.println(s"❌ Exception: ${e.getClass.getName}")
        System.err.println(s"❌ Message: ${e.getMessage}")
        e.printStackTrace()
        throw e
    }
  }

  def startHealthCheck(): Unit = {
    val healthCheckThread = new Thread(() => {
      try {
        val serverSocket = new java.net.ServerSocket(8081)
        println("✅ Health check server listening on port 8081")
        while (true) {
          val socket = serverSocket.accept()
          val out = new java.io.PrintWriter(socket.getOutputStream, true)
          out.println("HTTP/1.1 200 OK")
          out.println("Content-Type: text/plain")
          out.println("Content-Length: 2")
          out.println()
          out.println("OK")
          out.flush()
          socket.close()
        }
      } catch {
        case e: Exception => 
          System.err.println(s"❌ Health check server error: ${e.getMessage}")
          e.printStackTrace()
      }
    })
    healthCheckThread.setDaemon(true)
    healthCheckThread.start()
  }

  // ========== Domain Logic ==========

  private[flinkprocessor] def parseJson(json: String): TransactionEvent =
    try {
      val event = JsonUtils.fromJson[TransactionEvent](json)
      println(s"✅ Parsed transaction: actorId=${event.actorId}, domain=${event.domain}, amount=${event.amount}")
      event
    } catch {
      case e: Exception =>
        System.err.println(s"⚠️  Failed to parse JSON: ${e.getMessage}")
        System.err.println(s"⚠️  Raw JSON: $json")
        null
    }

  private[flinkprocessor] def isHighRisk(tuple: (TimedWindowAggregate, PredictResponse)): Boolean = {
    val (agg, response) = tuple
    val isHighRisk = response.fraudScore >= Config.Flink.FraudDetection.scoreThreshold
    if (isHighRisk) {
      println(s"🚨 HIGH RISK DETECTED: actor=${agg.actorId}, score=${response.fraudScore}")
    } else {
      println(s"✓ Low risk: actor=${agg.actorId}, score=${response.fraudScore}")
    }
    isHighRisk
  }

  private[flinkprocessor] def buildAlertWithTransactions(
    aggregate: TimedWindowAggregate,
    response: PredictResponse
  ): (Alert, Seq[TransactionEvent]) = {

    println(s"📝 Building alert for actor=${aggregate.actorId}, score=${response.fraudScore}")

    val patterns = aggregate.transactions
      .filter(_.isFraud)
      .map(_.pattern)
      .distinct
      .filter(_ != "regular")

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

    println(s"✅ Alert built: type=${alert.alertType}, severity=${alert.severity}")
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
  override def map(json: String): TransactionEvent = {
    println(s"📥 Received message from Kafka (${json.length} chars)")
    StreamProcessorJob.parseJson(json)
  }
}

@SerialVersionUID(101L)
class AggregateLogger extends MapFunction[TimedWindowAggregate, TimedWindowAggregate] {
  override def map(agg: TimedWindowAggregate): TimedWindowAggregate = {
    println(s"📊 Window: actor=${agg.actorId}, txns=${agg.transactionCount}, amount=${agg.totalAmount}, domain=${agg.domain}")
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
    val (alert, txs) = tuple
    println(s"🚨 ALERT: ${alert.severity} - actor=${alert.actorId}, score=${alert.fraudScore}, type=${alert.alertType}, txCount=${txs.length}")
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
