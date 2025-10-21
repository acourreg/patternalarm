package com.patternalarm.flinkprocessor.sink

import com.patternalarm.flinkprocessor.model.Alert
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import scalikejdbc._

import java.time.Instant

// Helper case class for test results
case class StoredAlert(
                        alertId: Long,
                        alertType: String,
                        domain: String,
                        actorId: String,
                        severity: String,
                        fraudScore: Int,
                        transactionCount: Int,
                        totalAmount: java.math.BigDecimal,
                        metadata: String
                      )

class FraudAlertSinkTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  private val jdbcUrl = "jdbc:h2:mem:test_fraud_alerts;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
  private val username = "sa"
  private val password = ""

  override def beforeAll(): Unit = {
    ConnectionPool.singleton(jdbcUrl, username, password)

    DB.autoCommit { implicit session =>
      sql"""
        CREATE TABLE fraud_alerts (
          alert_id BIGSERIAL PRIMARY KEY,
          alert_type VARCHAR(100) NOT NULL,
          domain VARCHAR(50) NOT NULL,
          actor_id VARCHAR(255) NOT NULL,
          severity VARCHAR(20) NOT NULL,
          fraud_score INT NOT NULL,
          transaction_count INT NOT NULL,
          total_amount DECIMAL(15, 2) NOT NULL,
          first_seen TIMESTAMP NOT NULL,
          last_seen TIMESTAMP NOT NULL,
          metadata VARCHAR(1000),
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
      """.execute.apply()
    }

    println("✅ H2 in-memory database initialized")
  }

  override def afterEach(): Unit = {
    DB.autoCommit { implicit session =>
      sql"DELETE FROM fraud_alerts".execute.apply()
    }
  }

  override def afterAll(): Unit = {
    ConnectionPool.closeAll()
    println("🔌 H2 connection pool closed")
  }

  behavior of "FraudAlertSink"

  it should "store a fraud alert in the database" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)
    sink.open(null)

    val alert = Alert(
      alertId = 0,
      alertType = "velocity_spike",
      domain = "gaming",
      actorId = "test_actor_123",
      severity = "HIGH",
      fraudScore = 85,
      transactionCount = 5,
      totalAmount = 2500.50,
      firstSeen = Instant.parse("2025-10-20T10:00:00Z"),
      lastSeen = Instant.parse("2025-10-20T10:01:00Z")
    )

    sink.invoke(alert, null.asInstanceOf[SinkFunction.Context])

    val stored = DB.readOnly { implicit session =>
      sql"""
        SELECT alert_id, alert_type, domain, actor_id, severity,
              fraud_score, transaction_count, total_amount, metadata
        FROM fraud_alerts
        WHERE actor_id = ${alert.actorId}
      """.map { rs =>
        StoredAlert(
          alertId = rs.long("alert_id"),
          alertType = rs.string("alert_type"),
          domain = rs.string("domain"),
          actorId = rs.string("actor_id"),
          severity = rs.string("severity"),
          fraudScore = rs.int("fraud_score"),
          transactionCount = rs.int("transaction_count"),
          totalAmount = rs.bigDecimal("total_amount"),
          metadata = rs.string("metadata")
        )
      }.single.apply()
    }

    stored should not be empty
    val result = stored.get

    result.alertId should be > 0L
    result.alertType shouldBe "velocity_spike"
    result.domain shouldBe "gaming"
    result.actorId shouldBe "test_actor_123"
    result.severity shouldBe "HIGH"
    result.fraudScore shouldBe 85
    result.transactionCount shouldBe 5
    result.totalAmount shouldBe new java.math.BigDecimal("2500.50")
    result.metadata should include("window_seconds")
    result.metadata should include("confidence")

    sink.close()

    info(s"✅ Alert ${result.alertId} successfully stored and verified")
  }

  it should "store multiple alerts for the same actor" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)
    sink.open(null)

    val alert1 = Alert(
      alertId = 0,
      alertType = "velocity_spike",
      domain = "gaming",
      actorId = "repeat_offender",
      severity = "HIGH",
      fraudScore = 80,
      transactionCount = 3,
      totalAmount = 1000.0,
      firstSeen = Instant.parse("2025-10-20T10:00:00Z"),
      lastSeen = Instant.parse("2025-10-20T10:01:00Z")
    )

    val alert2 = Alert(
      alertId = 0,
      alertType = "suspicious_pattern",
      domain = "gaming",
      actorId = "repeat_offender",
      severity = "CRITICAL",
      fraudScore = 95,
      transactionCount = 10,
      totalAmount = 5000.0,
      firstSeen = Instant.parse("2025-10-20T11:00:00Z"),
      lastSeen = Instant.parse("2025-10-20T11:01:00Z")
    )

    sink.invoke(alert1, null.asInstanceOf[SinkFunction.Context])
    sink.invoke(alert2, null.asInstanceOf[SinkFunction.Context])

    val alertCount = DB.readOnly { implicit session =>
      sql"""
        SELECT COUNT(*) as cnt
        FROM fraud_alerts
        WHERE actor_id = 'repeat_offender'
      """.map(_.int("cnt")).single.apply().get
    }

    alertCount shouldBe 2

    // Verify both alerts exist with different types
    val alertTypes = DB.readOnly { implicit session =>
      sql"""
        SELECT alert_type
        FROM fraud_alerts
        WHERE actor_id = 'repeat_offender'
        ORDER BY alert_type
      """.map(_.string("alert_type")).list.apply()
    }

    alertTypes should contain allOf ("suspicious_pattern", "velocity_spike")

    sink.close()

    info("✅ Multiple alerts stored for same actor")
  }

  it should "store alerts with different severity levels" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)
    sink.open(null)

    val severities = Seq("LOW", "MEDIUM", "HIGH", "CRITICAL")

    severities.zipWithIndex.foreach { case (severity, index) =>
      val alert = Alert(
        alertId = 0,
        alertType = "test_pattern",
        domain = "testing",
        actorId = s"actor_$index",
        severity = severity,
        fraudScore = 60 + (index * 10),
        transactionCount = index + 1,
        totalAmount = (index + 1) * 100.0,
        firstSeen = Instant.now(),
        lastSeen = Instant.now()
      )

      sink.invoke(alert, null.asInstanceOf[SinkFunction.Context])
    }

    val severityCounts = DB.readOnly { implicit session =>
      sql"""
        SELECT severity, COUNT(*) as cnt
        FROM fraud_alerts
        WHERE domain = 'testing'
        GROUP BY severity
        ORDER BY severity
      """.map { rs =>
        (rs.string("severity"), rs.int("cnt"))
      }.list.apply()
    }

    severityCounts should have size 4
    severityCounts.map(_._1).sorted shouldBe Seq("CRITICAL", "HIGH", "LOW", "MEDIUM")
    severityCounts.foreach { case (_, count) => count shouldBe 1 }

    sink.close()

    info("✅ All severity levels stored correctly")
  }

  it should "handle large transaction amounts correctly" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)
    sink.open(null)

    val alert = Alert(
      alertId = 0,
      alertType = "high_value",
      domain = "finance",
      actorId = "whale_actor",
      severity = "CRITICAL",
      fraudScore = 99,
      transactionCount = 1,
      totalAmount = 9999999.99,
      firstSeen = Instant.now(),
      lastSeen = Instant.now()
    )

    sink.invoke(alert, null.asInstanceOf[SinkFunction.Context])

    val storedAmount = DB.readOnly { implicit session =>
      sql"""
        SELECT total_amount
        FROM fraud_alerts
        WHERE actor_id = 'whale_actor'
      """.map(_.bigDecimal("total_amount")).single.apply().get
    }

    storedAmount shouldBe new java.math.BigDecimal("9999999.99")

    sink.close()

    info("✅ Large amounts handled correctly")
  }

  it should "fail gracefully when database connection fails" in {
    val badJdbcUrl = "jdbc:h2:mem:nonexistent;IFEXISTS=TRUE"
    val sink = new FraudAlertSink(badJdbcUrl, username, password)

    val alert = Alert(
      alertId = 0,
      alertType = "test",
      domain = "test",
      actorId = "test",
      severity = "LOW",
      fraudScore = 50,
      transactionCount = 1,
      totalAmount = 100.0,
      firstSeen = Instant.now(),
      lastSeen = Instant.now()
    )

    an[Exception] should be thrownBy {
      sink.open(null)
      sink.invoke(alert, null.asInstanceOf[SinkFunction.Context])
    }

    info("✅ Handled database unavailability")
  }

  it should "store metadata as valid JSON with expected fields" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)
    sink.open(null)

    val alert = Alert(
      alertId = 0,
      alertType = "velocity_spike",
      domain = "gaming",
      actorId = "metadata_test",
      severity = "HIGH",
      fraudScore = 85,
      transactionCount = 5,
      totalAmount = 2500.50,
      firstSeen = Instant.parse("2025-10-20T10:00:00Z"),
      lastSeen = Instant.parse("2025-10-20T10:01:00Z")
    )

    sink.invoke(alert, null.asInstanceOf[SinkFunction.Context])

    val metadata = DB.readOnly { implicit session =>
      sql"""
        SELECT metadata
        FROM fraud_alerts
        WHERE actor_id = 'metadata_test'
      """.map(_.string("metadata")).single.apply().get
    }

    // Verify it's valid JSON by parsing with Jackson
    import com.fasterxml.jackson.databind.ObjectMapper
    import com.fasterxml.jackson.module.scala.DefaultScalaModule

    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    noException should be thrownBy {
      val parsed = mapper.readTree(metadata)

      // Verify expected fields exist
      parsed.has("window_seconds") shouldBe true
      parsed.has("confidence") shouldBe true
      parsed.has("alert_type") shouldBe true
      parsed.has("severity") shouldBe true

      // Verify values
      parsed.get("confidence").asInt() shouldBe 85
      parsed.get("alert_type").asText() shouldBe "velocity_spike"
      parsed.get("severity").asText() shouldBe "HIGH"
      parsed.get("window_seconds").asInt() shouldBe 60
    }

    sink.close()

    info("✅ Metadata stored as valid JSON with correct values")
  }

  it should "generate unique alert IDs for each alert" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)
    sink.open(null)

    val alerts = (1 to 5).map { i =>
      Alert(
        alertId = 0,
        alertType = "test_pattern",
        domain = "testing",
        actorId = s"actor_$i",
        severity = "MEDIUM",
        fraudScore = 70,
        transactionCount = 1,
        totalAmount = 100.0,
        firstSeen = Instant.now(),
        lastSeen = Instant.now()
      )
    }

    alerts.foreach { alert =>
      sink.invoke(alert, null.asInstanceOf[SinkFunction.Context])
    }

    val alertIds = DB.readOnly { implicit session =>
      sql"""
        SELECT alert_id
        FROM fraud_alerts
        WHERE domain = 'testing'
        ORDER BY alert_id
      """.map(_.long("alert_id")).list.apply()
    }

    alertIds should have size 9
    alertIds.distinct should have size 9 // All unique
    alertIds.foreach { id => id should be > 0L }

    sink.close()

    info("✅ Unique alert IDs generated")
  }

  it should "store timestamps correctly" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)
    sink.open(null)

    val firstSeen = Instant.parse("2025-10-20T10:00:00Z")
    val lastSeen = Instant.parse("2025-10-20T10:05:00Z")

    val alert = Alert(
      alertId = 0,
      alertType = "test_pattern",
      domain = "testing",
      actorId = "timestamp_test",
      severity = "MEDIUM",
      fraudScore = 70,
      transactionCount = 3,
      totalAmount = 500.0,
      firstSeen = firstSeen,
      lastSeen = lastSeen
    )

    sink.invoke(alert, null.asInstanceOf[SinkFunction.Context])

    val timestamps = DB.readOnly { implicit session =>
      sql"""
        SELECT first_seen, last_seen
        FROM fraud_alerts
        WHERE actor_id = 'timestamp_test'
      """.map { rs =>
        (rs.timestamp("first_seen").toInstant, rs.timestamp("last_seen").toInstant)
      }.single.apply().get
    }

    val (storedFirstSeen, storedLastSeen) = timestamps

    storedFirstSeen shouldBe firstSeen
    storedLastSeen shouldBe lastSeen

    sink.close()

    info("✅ Timestamps stored correctly")
  }

  it should "handle alerts with zero transaction count" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)
    sink.open(null)

    val alert = Alert(
      alertId = 0,
      alertType = "suspicious_activity",
      domain = "testing",
      actorId = "zero_txn_actor",
      severity = "LOW",
      fraudScore = 60,
      transactionCount = 0,
      totalAmount = 0.0,
      firstSeen = Instant.now(),
      lastSeen = Instant.now()
    )

    noException should be thrownBy {
      sink.invoke(alert, null.asInstanceOf[SinkFunction.Context])
    }

    val stored = DB.readOnly { implicit session =>
      sql"""
        SELECT transaction_count, total_amount
        FROM fraud_alerts
        WHERE actor_id = 'zero_txn_actor'
      """.map { rs =>
        (rs.int("transaction_count"), rs.bigDecimal("total_amount"))
      }.single.apply().get
    }

    val (txnCount, amount) = stored
    txnCount shouldBe 0
    amount shouldBe new java.math.BigDecimal("0.00")

    sink.close()

    info("✅ Zero transaction count handled correctly")
  }
}