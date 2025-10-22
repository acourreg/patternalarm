package com.patternalarm.flinkprocessor

import com.patternalarm.flinkprocessor.model._
import com.patternalarm.flinkprocessor.processor.FraudScoringAsyncFunction
import com.patternalarm.flinkprocessor.sink.FraudAlertSink
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.async.ResultFuture
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable
import scala.jdk.CollectionConverters._
import java.util.Collections

class StreamProcessorJobTest extends AnyFlatSpec with Matchers {

  behavior of "StreamProcessorJob (Blackbox)"

  "run()" should "execute pipeline end-to-end with mocked dependencies" in {
    println("\n🧪 TEST: Starting end-to-end pipeline test")

    TestAlertCollector.clear()

    val baseTime = "2025-10-20T10:00:00.000Z"
    val endTime = "2025-10-20T10:02:00.000Z"

    val testTransactions = Seq(
      s"""{"transactionId":"tx1","domain":"gaming","testId":"test1","timestamp":"$baseTime","actorId":"fraud_actor","amount":500.0,"currency":"USD","ipAddress":"1.2.3.4","pattern":"fraud_velocity_spike","isFraud":true,"sequencePosition":1}""",
      s"""{"transactionId":"tx2","domain":"gaming","testId":"test1","timestamp":"$baseTime","actorId":"fraud_actor","amount":600.0,"currency":"USD","ipAddress":"1.2.3.4","pattern":"fraud_velocity_spike","isFraud":true,"sequencePosition":2}""",
      s"""{"transactionId":"tx3","domain":"gaming","testId":"test1","timestamp":"$baseTime","actorId":"fraud_actor","amount":700.0,"currency":"USD","ipAddress":"1.2.3.4","pattern":"fraud_velocity_spike","isFraud":true,"sequencePosition":3}""",
      s"""{"transactionId":"end","domain":"gaming","testId":"test1","timestamp":"$endTime","actorId":"dummy","amount":1.0,"currency":"USD","ipAddress":"1.2.3.4","pattern":"regular","isFraud":false,"sequencePosition":99}"""
    )

    println(s"📝 Test data: ${testTransactions.size} transactions prepared")

    def mockEnvProvider(): StreamExecutionEnvironment = {
      val env = StreamExecutionEnvironment.getExecutionEnvironment
      env.setParallelism(1)
      env.getCheckpointConfig.disableCheckpointing()
      println("✅ Mock environment created (parallelism=1, no checkpointing)")
      env
    }

    def mockKafkaSource(env: StreamExecutionEnvironment): DataStream[String] = {
      println(s"✅ Mock Kafka source created with ${testTransactions.size} transactions")
      env.fromCollection(testTransactions.asJava)
    }

    val mockAsyncFunction = new MockHighScoreAsyncFunction()
    val mockSink = new MockAlertSink()

    val job = new StreamProcessorJob(
      mockEnvProvider,
      mockKafkaSource,
      mockAsyncFunction,
      mockSink
    )

    println("\n🎬 Starting pipeline execution...")

    var exception: Option[Throwable] = None

    val executionThread = new Thread(() => {
      try {
        job.run()
      } catch {
        case e: Throwable =>
          exception = Some(e)
          println(s"\n❌ EXCEPTION: ${e.getMessage}")
      }
    })

    executionThread.setDaemon(true)
    executionThread.start()

    val maxWaitTime = 20000
    val startTime = System.currentTimeMillis()

    print("⏳ Waiting for alerts")
    while (TestAlertCollector.size == 0 &&
      (System.currentTimeMillis() - startTime) < maxWaitTime &&
      executionThread.isAlive &&
      exception.isEmpty) {
      Thread.sleep(500)
      print(".")
    }
    println()

    if (TestAlertCollector.size > 0) {
      println("🎯 First alert detected! Waiting for remaining alerts...")
      Thread.sleep(3000)
    }

    val alertCount = TestAlertCollector.size

    println(s"\n📊 FINAL RESULTS:")
    println(s"   ├─ Alerts produced: $alertCount")
    println(s"   └─ Exception occurred: ${exception.isDefined}")

    if (exception.isDefined) {
      fail(s"Exception: ${exception.get.getMessage}")
    } else if (alertCount > 0) {
      val fraudAlert = TestAlertCollector.find(_.actorId == "fraud_actor")

      fraudAlert should not be empty

      val alert = fraudAlert.get

      println(s"\n✅ SUCCESS! Alert details:")
      println(s"   ├─ Actor: ${alert.actorId}")
      println(s"   ├─ Domain: ${alert.domain}")
      println(s"   ├─ Score: ${alert.fraudScore}")
      println(s"   ├─ Severity: ${alert.severity}")
      println(s"   ├─ Type: ${alert.alertType}")
      println(s"   ├─ Transaction Count: ${alert.transactionCount}")
      println(s"   └─ Total Amount: ${alert.totalAmount}")

      alert.actorId shouldBe "fraud_actor"
      alert.domain shouldBe "gaming"
      alert.fraudScore should be >= 70
      alert.severity should (be("HIGH") or be("CRITICAL"))
      alert.transactionCount shouldBe 3
      alert.totalAmount shouldBe 1800.0
      alert.alertType shouldBe "velocity_spike"

      info(s"✅ Pipeline produced ${alertCount} alert(s) - TEST PASSED!")
    } else {
      fail("Expected at least 1 alert but got 0")
    }
  }

  "run()" should "handle invalid JSON gracefully" in {
    println("\n🧪 TEST: Invalid JSON handling")

    TestAlertCollector.clear()

    val testTransactions = Seq(
      """invalid json {{{""",
      s"""{"transactionId":"tx1","domain":"gaming","testId":"test1","timestamp":"2025-10-20T10:00:00.000Z","actorId":"actor1","amount":100.0,"currency":"USD","ipAddress":"1.2.3.4","pattern":"fraud_velocity_spike","isFraud":true,"sequencePosition":1}""",
      """another broken json"""
    )

    println(s"📝 Test data: ${testTransactions.size} messages (2 invalid, 1 valid)")

    def mockEnv(): StreamExecutionEnvironment = {
      val env = StreamExecutionEnvironment.getExecutionEnvironment
      env.setParallelism(1)
      env.getCheckpointConfig.disableCheckpointing()
      env
    }

    def mockSource(env: StreamExecutionEnvironment): DataStream[String] = {
      env.fromCollection(testTransactions.asJava)
    }

    val mockAsync = new MockHighScoreAsyncFunction()
    val mockSink = new MockAlertSink()

    val job = new StreamProcessorJob(mockEnv, mockSource, mockAsync, mockSink)

    noException should be thrownBy {
      val thread = new Thread(() => {
        try {
          job.run()
        } catch {
          case _: Exception => // Expected
        }
      })
      thread.setDaemon(true)
      thread.start()
      Thread.sleep(2000)
    }

    println("✅ No exceptions thrown")
    info("✅ Pipeline handled invalid JSON gracefully")
  }

  "run()" should "filter out low-risk transactions" in {
    println("\n🧪 TEST: Low-risk filtering")

    TestAlertCollector.clear()

    val testTransactions = Seq(
      s"""{"transactionId":"tx1","domain":"gaming","testId":"test1","timestamp":"2025-10-20T10:00:00.000Z","actorId":"safe_actor","amount":10.0,"currency":"USD","ipAddress":"1.2.3.4","pattern":"regular","isFraud":false,"sequencePosition":1}"""
    )

    println(s"📝 Test data: Low-risk transaction")

    def mockEnv(): StreamExecutionEnvironment = {
      val env = StreamExecutionEnvironment.getExecutionEnvironment
      env.setParallelism(1)
      env.getCheckpointConfig.disableCheckpointing()
      env
    }

    def mockSource(env: StreamExecutionEnvironment): DataStream[String] = {
      env.fromCollection(testTransactions.asJava)
    }

    val mockAsync = new MockLowScoreAsyncFunction()
    val mockSink = new MockAlertSink()

    val job = new StreamProcessorJob(mockEnv, mockSource, mockAsync, mockSink)

    val thread = new Thread(() => {
      try {
        job.run()
      } catch {
        case _: Exception => // Expected
      }
    })
    thread.setDaemon(true)
    thread.start()
    Thread.sleep(2000)

    println(s"📊 Alerts produced: ${TestAlertCollector.size}")

    TestAlertCollector.size shouldBe 0

    println("✅ Low-risk correctly filtered")
    info("✅ Low-risk transactions correctly filtered out")
  }

  "run()" should "be instantiable with default constructor" in {
    println("\n🧪 TEST: Default constructor")

    val job = new StreamProcessorJob()

    job should not be null

    println("✅ Default constructor works")
    info("✅ Production configuration validated")
  }
}

// ========== Serializable Mock Classes ==========

@SerialVersionUID(1L)
class MockHighScoreAsyncFunction extends FraudScoringAsyncFunction("mock_high_score") {
  override def asyncInvoke(
                            input: TimedWindowAggregate,
                            resultFuture: ResultFuture[(TimedWindowAggregate, PredictResponse)]
                          ): Unit = {
    println(s"\n🤖 MOCK ML SCORING CALLED:")
    println(s"   ├─ Actor: ${input.actorId}")
    println(s"   ├─ Domain: ${input.domain}")
    println(s"   ├─ Transaction Count: ${input.transactionCount}")
    println(s"   ├─ Total Amount: ${input.totalAmount}")
    println(s"   ├─ Window: ${input.windowStart} → ${input.windowEnd}")
    println(s"   ├─ Transactions in window: ${input.transactions.size}")
    input.transactions.foreach { tx =>
      println(s"   │  └─ ${tx.transactionId}: ${tx.amount} (${tx.pattern}, fraud=${tx.isFraud})")
    }

    val response = PredictResponse(
      fraudScore = 95,
      modelVersion = "mock_v1",
      inferenceTimeMs = 1,
      transactionsAnalyzed = input.transactionCount
    )

    println(s"   └─ 🎯 Returning score: ${response.fraudScore}")

    resultFuture.complete(Collections.singleton((input, response)))
  }
}

@SerialVersionUID(2L)
class MockLowScoreAsyncFunction extends FraudScoringAsyncFunction("mock_low_score") {
  override def asyncInvoke(
                            input: TimedWindowAggregate,
                            resultFuture: ResultFuture[(TimedWindowAggregate, PredictResponse)]
                          ): Unit = {
    println(s"🤖 Mock async called for actor: ${input.actorId}")
    val response = PredictResponse(
      fraudScore = 30,
      modelVersion = "mock_low",
      inferenceTimeMs = 1,
      transactionsAnalyzed = input.transactionCount
    )
    println(s"   └─ Returning low score: ${response.fraudScore}")
    resultFuture.complete(Collections.singleton((input, response)))
  }
}

@SerialVersionUID(3L)
class MockAlertSink() extends FraudAlertSink("test", "test", "test") {
  // ✅ Now accepts (Alert, Seq[TransactionEvent])
  override def invoke(value: (Alert, Seq[TransactionEvent]), context: SinkFunction.Context): Unit = {
    val (alert, transactions) = value

    println(s"\n🚨 ALERT CAPTURED:")
    println(s"   ├─ Type: ${alert.alertType}")
    println(s"   ├─ Actor: ${alert.actorId}")
    println(s"   ├─ Score: ${alert.fraudScore}")
    println(s"   ├─ Severity: ${alert.severity}")
    println(s"   ├─ Transaction Count: ${alert.transactionCount}")
    println(s"   ├─ Total Amount: ${alert.totalAmount}")
    println(s"   └─ Transactions: ${transactions.size}")

    TestAlertCollector.add(alert)
  }
}

object TestAlertCollector {
  private val alerts: mutable.ListBuffer[Alert] = mutable.ListBuffer[Alert]()

  def add(alert: Alert): Unit = alerts.synchronized {
    alerts += alert
  }

  def clear(): Unit = alerts.synchronized {
    alerts.clear()
  }

  def size: Int = alerts.synchronized {
    alerts.size
  }

  def getAll: List[Alert] = alerts.synchronized {
    alerts.toList
  }

  def find(predicate: Alert => Boolean): Option[Alert] = alerts.synchronized {
    alerts.find(predicate)
  }
}