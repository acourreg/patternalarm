package com.patternalarm.flinkprocessor.utils

import com.patternalarm.flinkprocessor.model.{TransactionEvent, PredictResponse}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.Instant

class JsonUtilsTest extends AnyFlatSpec with Matchers {

  "JsonUtils" should "serialize and deserialize PredictResponse" in {
    val response = PredictResponse(
      fraudScore = 85,
      modelVersion = "v1.0-test",
      inferenceTimeMs = 12,
      transactionsAnalyzed = 5
    )

    val json = JsonUtils.toJson(response)
    val deserialized = JsonUtils.fromJson[PredictResponse](json)

    deserialized shouldBe response
  }

  it should "deserialize JSON with extra fields (ignore unknown)" in {
    val jsonWithExtra =
      """{
        |  "fraudScore": 75,
        |  "modelVersion": "v1.0",
        |  "inferenceTimeMs": 10,
        |  "transactionsAnalyzed": 3,
        |  "extraField": "should be ignored",
        |  "anotherExtra": 999
        |}""".stripMargin

    val result = JsonUtils.fromJson[PredictResponse](jsonWithExtra)

    result.fraudScore shouldBe 75
    result.modelVersion shouldBe "v1.0"
  }

  it should "serialize and deserialize TransactionEvent with all fields" in {
    val event = TransactionEvent(
      transactionId = "TXN123",
      domain = "gaming",
      testId = "test-001",
      timestamp = Instant.parse("2025-10-16T18:00:00Z"),
      actorId = "A123",
      amount = 150.0,
      currency = "USD",
      ipAddress = "192.168.1.1",
      pattern = "fraud_velocity_spike",
      isFraud = true,
      sequencePosition = 0,
      playerId = Some("P123"),
      gameId = Some("Fortnite"),
      deviceId = Some("DEV123")
    )

    val json = JsonUtils.toJson(event)
    val deserialized = JsonUtils.fromJson[TransactionEvent](json)

    deserialized.transactionId shouldBe event.transactionId
    deserialized.amount shouldBe event.amount
    deserialized.playerId shouldBe Some("P123")
  }

  it should "handle Option fields correctly (None values)" in {
    val event = TransactionEvent(
      transactionId = "TXN456",
      domain = "fintech",
      testId = "test-002",
      timestamp = Instant.now(),
      actorId = "A456",
      amount = 200.0,
      currency = "EUR",
      ipAddress = "10.0.0.1",
      pattern = "regular_pattern",
      isFraud = false,
      sequencePosition = 0
      // All Option fields are None (default)
    )

    val json = JsonUtils.toJson(event)
    val deserialized = JsonUtils.fromJson[TransactionEvent](json)

    deserialized.playerId shouldBe None
    deserialized.gameId shouldBe None
    deserialized.accountFrom shouldBe None
  }

  it should "handle Instant timestamps correctly" in {
    val timestamp = Instant.parse("2025-10-16T20:30:15.123456Z")

    val response = PredictResponse(
      fraudScore = 90,
      modelVersion = "v1.0",
      inferenceTimeMs = 15,
      transactionsAnalyzed = 7
    )

    val json = JsonUtils.toJson(response)
    json should include("fraudScore")
    json should include("90")
  }

  it should "throw exception for invalid JSON" in {
    val invalidJson = """{"invalid": "json", missing closing brace"""

    assertThrows[Exception] {
      JsonUtils.fromJson[PredictResponse](invalidJson)
    }
  }
}