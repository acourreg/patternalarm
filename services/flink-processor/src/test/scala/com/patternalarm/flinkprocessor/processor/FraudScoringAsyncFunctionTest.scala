package com.patternalarm.flinkprocessor.processor

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, urlEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.patternalarm.flinkprocessor.model._
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.async.ResultFuture
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.util
import java.util.concurrent.{CompletableFuture, TimeUnit}
import scala.collection.JavaConverters._

class FraudScoringAsyncFunctionTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll {

  // Mock HTTP server
  private val wireMockServer = new WireMockServer(wireMockConfig().dynamicPort())
  private var mockServerUrl: String = _

  override def beforeAll(): Unit = {
    wireMockServer.start()
    mockServerUrl = s"http://localhost:${wireMockServer.port()}"
  }

  override def afterAll(): Unit = {
    wireMockServer.stop()
  }

  // Helper to create test aggregate
  def createTestAggregate(): TimedWindowAggregate = {
    val event1 = TransactionEvent(
      transactionId = "TXN001",
      domain = "gaming",
      testId = "test-001",
      timestamp = Instant.now(),
      actorId = "A123",
      amount = 150.0,
      currency = "USD",
      ipAddress = "192.168.1.1",
      pattern = "fraud_velocity_spike",
      isFraud = true,
      sequencePosition = 0
    )

    TimedWindowAggregate(
      actorId = "A123",
      domain = "gaming",
      transactionCount = 1,
      totalAmount = 150.0,
      windowStart = Instant.now().minusSeconds(60),
      windowEnd = Instant.now(),
      transactions = List(event1)
    )
  }

  "FraudScoringAsyncFunction" should "successfully call FastAPI and parse response" in {
    // Setup mock server response
    wireMockServer.stubFor(
      post(urlEqualTo("/predict"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
              {
                "fraud_score": 85,
                "model_version": "v1.0-test",
                "inference_time_ms": 12,
                "transactions_analyzed": 1
              }
            """)
        )
    )

    // Create function and initialize
    val function = new FraudScoringAsyncFunction(mockServerUrl)
    function.open(new Configuration())

    // Create test data
    val aggregate = createTestAggregate()
    val resultFuture = new TestResultFuture[(TimedWindowAggregate, PredictResponse)]()

    // Call async function
    function.asyncInvoke(aggregate, resultFuture)

    // Wait for result (with timeout)
    val result = resultFuture.get(5, TimeUnit.SECONDS)

    // Verify
    result should have size 1
    val (returnedAggregate, response) = result.head

    returnedAggregate shouldBe aggregate
    response.fraudScore shouldBe 85
    response.modelVersion shouldBe "v1.0-test"
    response.transactionsAnalyzed shouldBe 1

    // Cleanup
    function.close()
  }

  it should "handle FastAPI error responses" in {
    // Setup mock server to return error
    wireMockServer.stubFor(
      post(urlEqualTo("/predict"))
        .willReturn(
          aResponse()
            .withStatus(500)
            .withBody("Internal Server Error")
        )
    )

    val function = new FraudScoringAsyncFunction(mockServerUrl)
    function.open(new Configuration())

    val aggregate = createTestAggregate()
    val resultFuture = new TestResultFuture[(TimedWindowAggregate, PredictResponse)]()

    function.asyncInvoke(aggregate, resultFuture)

    // Should complete exceptionally
    val exception = intercept[Exception] {
      resultFuture.get(5, TimeUnit.SECONDS)
    }

    exception.getMessage should include("FastAPI error 500")

    function.close()
  }

  it should "handle network failures" in {
    // Use invalid URL to simulate network failure
    val function = new FraudScoringAsyncFunction("http://invalid-host-that-does-not-exist:9999")
    function.open(new Configuration())

    val aggregate = createTestAggregate()
    val resultFuture = new TestResultFuture[(TimedWindowAggregate, PredictResponse)]()

    function.asyncInvoke(aggregate, resultFuture)

    // Should complete exceptionally
    val exception = intercept[Exception] {
      resultFuture.get(5, TimeUnit.SECONDS)
    }

    exception.getMessage should include("HTTP call failed")

    function.close()
  }

  it should "handle invalid JSON responses" in {
    // Setup mock server to return invalid JSON
    wireMockServer.stubFor(
      post(urlEqualTo("/predict"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{invalid json}")
        )
    )

    val function = new FraudScoringAsyncFunction(mockServerUrl)
    function.open(new Configuration())

    val aggregate = createTestAggregate()
    val resultFuture = new TestResultFuture[(TimedWindowAggregate, PredictResponse)]()

    function.asyncInvoke(aggregate, resultFuture)

    // Should complete exceptionally
    val exception = intercept[Exception] {
      resultFuture.get(5, TimeUnit.SECONDS)
    }

    exception.getMessage should include("Failed to parse response")

    function.close()
  }

  it should "handle timeout correctly" in {
    val function = new FraudScoringAsyncFunction(mockServerUrl)
    function.open(new Configuration())

    val aggregate = createTestAggregate()
    val resultFuture = new TestResultFuture[(TimedWindowAggregate, PredictResponse)]()

    // Call timeout directly
    function.timeout(aggregate, resultFuture)

    // Should complete exceptionally
    val exception = intercept[Exception] {
      resultFuture.get(5, TimeUnit.SECONDS)
    }

    exception.getMessage should include("Timeout for actor A123")

    function.close()
  }


  // Test implementation of ResultFuture for testing
  class TestResultFuture[T] extends ResultFuture[T] {
    private val future = new CompletableFuture[java.util.Collection[T]]()

    override def completeExceptionally(throwable: Throwable): Unit = {
      future.completeExceptionally(throwable)
    }

    def get(timeout: Long, unit: TimeUnit): Iterable[T] = {
      future.get(timeout, unit).asScala
    }

    override def complete(result: util.Collection[T]): Unit = {
      future.complete(result)
    }

  }
}
