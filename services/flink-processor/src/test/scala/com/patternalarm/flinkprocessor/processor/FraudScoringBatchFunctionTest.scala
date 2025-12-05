package com.patternalarm.flinkprocessor.processor

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.patternalarm.flinkprocessor.model._
import org.apache.flink.configuration.Configuration
import org.apache.flink.util.Collector
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.collection.mutable.ArrayBuffer

class FraudScoringBatchFunctionTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  private val wireMockServer = new WireMockServer(wireMockConfig().dynamicPort())
  private var mockServerUrl: String = _

  override def beforeAll(): Unit = {
    wireMockServer.start()
    mockServerUrl = s"http://localhost:${wireMockServer.port()}"
  }

  override def afterAll(): Unit = {
    wireMockServer.stop()
  }

  def createTestAggregate(actorId: String, isFraud: Boolean = true): TimedWindowAggregate = {
    val event = TransactionEvent(
      transactionId = s"TXN-$actorId",
      domain = "gaming",
      testId = "test-001",
      timestamp = Instant.now(),
      actorId = actorId,
      amount = 150.0,
      currency = "USD",
      ipAddress = "192.168.1.1",
      pattern = if (isFraud) "fraud_velocity_spike" else "regular_casual_player",
      isFraud = isFraud,
      sequencePosition = 0
    )

    TimedWindowAggregate(
      actorId = actorId,
      domain = "gaming",
      transactionCount = 1,
      totalAmount = 150.0,
      windowStart = Instant.now().minusSeconds(60),
      windowEnd = Instant.now(),
      transactions = List(event)
    )
  }

  "FraudScoringBatchFunction" should "send correct JSON format to /predict/batch" in {
    // Setup mock - verify request format
    wireMockServer.stubFor(
      post(urlEqualTo("/predict/batch"))
        .withRequestBody(matchingJsonPath("$.predictions"))  // Must have "predictions" field
        .withRequestBody(matchingJsonPath("$.predictions[0].actor_id"))
        .withRequestBody(matchingJsonPath("$.predictions[0].domain"))
        .withRequestBody(matchingJsonPath("$.predictions[0].transactions"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
              {
                "predictions": [
                  {"actor_id": "A001", "fraud_type": "fraud_velocity_spike", "is_fraud": true, "confidence": 0.85, "transactions_analyzed": 1, "total_amount": 150.0, "time_window_sec": 60.0, "ml_version": "spark-v1.0", "inference_time_ms": 50.0},
                  {"actor_id": "A002", "fraud_type": "regular", "is_fraud": false, "confidence": 0.95, "transactions_analyzed": 1, "total_amount": 150.0, "time_window_sec": 60.0, "ml_version": "spark-v1.0", "inference_time_ms": 50.0}
                ],
                "ml_version": "spark-v1.0",
                "total_inference_time_ms": 100.0,
                "actors_analyzed": 2
              }
            """)
        )
    )

    val function = new FraudScoringBatchFunction(mockServerUrl, batchSize = 2, flushIntervalMs = 10000)
    function.open(new Configuration())

    val collector = new TestCollector[(TimedWindowAggregate, PredictResponse)]()

    // Process 2 elements (triggers flush at batchSize=2)
    function.processElement(createTestAggregate("A001", isFraud = true), null, collector)
    function.processElement(createTestAggregate("A002", isFraud = false), null, collector)

    // Verify results
    collector.results should have size 2

    val (agg1, resp1) = collector.results.find(_._1.actorId == "A001").get
    resp1.fraudScore shouldBe 85
    resp1.modelVersion shouldBe "spark-v1.0"

    val (agg2, resp2) = collector.results.find(_._1.actorId == "A002").get
    resp2.fraudScore shouldBe 95

    // Verify request was made with correct format
    wireMockServer.verify(postRequestedFor(urlEqualTo("/predict/batch"))
      .withRequestBody(matchingJsonPath("$.predictions")))

    function.close()
  }

  it should "emit fallback scores on API error" in {
    wireMockServer.stubFor(
      post(urlEqualTo("/predict/batch"))
        .willReturn(aResponse().withStatus(500).withBody("Internal Server Error"))
    )

    val function = new FraudScoringBatchFunction(mockServerUrl, batchSize = 1, flushIntervalMs = 10000)
    function.open(new Configuration())

    val collector = new TestCollector[(TimedWindowAggregate, PredictResponse)]()
    function.processElement(createTestAggregate("A003"), null, collector)

    collector.results should have size 1
    collector.results.head._2.fraudScore shouldBe 0
    collector.results.head._2.modelVersion shouldBe "error"

    function.close()
  }

  class TestCollector[T] extends Collector[T] {
    val results: ArrayBuffer[T] = ArrayBuffer.empty
    override def collect(record: T): Unit = results += record
    override def close(): Unit = {}
  }
}