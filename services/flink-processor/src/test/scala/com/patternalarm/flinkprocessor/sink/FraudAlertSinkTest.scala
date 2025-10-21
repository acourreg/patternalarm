package com.patternalarm.flinkprocessor.sink

import com.patternalarm.flinkprocessor.model.{AlertDetail, TransactionEvent}
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
      // Create fraud_alerts table
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

      // Create suspicious_transactions table
      sql"""
        CREATE TABLE suspicious_transactions (
          id BIGSERIAL PRIMARY KEY,
          alert_id BIGINT NOT NULL,
          transaction_id VARCHAR(255) NOT NULL,
          domain VARCHAR(50) NOT NULL,
          test_id VARCHAR(255) NOT NULL,
          timestamp TIMESTAMP NOT NULL,
          actor_id VARCHAR(255) NOT NULL,
          amount DECIMAL(15, 2) NOT NULL,
          currency VARCHAR(10) NOT NULL,
          ip_address VARCHAR(50) NOT NULL,
          pattern VARCHAR(100) NOT NULL,
          is_fraud BOOLEAN NOT NULL,
          sequence_position INT NOT NULL,
          player_id VARCHAR(100),
          game_id VARCHAR(100),
          item_type VARCHAR(50),
          item_name VARCHAR(100),
          payment_method VARCHAR(50),
          device_id VARCHAR(100),
          session_length_sec INT,
          account_from VARCHAR(100),
          account_to VARCHAR(100),
          transfer_type VARCHAR(50),
          country_from VARCHAR(10),
          country_to VARCHAR(10),
          purpose VARCHAR(100),
          user_id VARCHAR(100),
          cart_items TEXT,
          shipping_address TEXT,
          billing_address TEXT,
          device_fingerprint VARCHAR(100),
          session_duration_sec INT,
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          FOREIGN KEY (alert_id) REFERENCES fraud_alerts(alert_id)
        )
      """.execute.apply()
    }

    println("✅ H2 in-memory database initialized")
  }

  override def afterEach(): Unit = {
    try {
      DB.autoCommit { implicit session =>
        sql"DELETE FROM suspicious_transactions".execute.apply()
        sql"DELETE FROM fraud_alerts".execute.apply()
      }
    } catch {
      case _: Exception =>
      // Ignore cleanup errors (e.g., from bad connection test)
      // Connection pool may not exist after "fail gracefully" test
    }
  }

  override def afterAll(): Unit = {
    ConnectionPool.closeAll()
    println("🔌 H2 connection pool closed")
  }

  // Helper to create test transaction
  def createTestTransaction(
                             txnId: String,
                             actorId: String,
                             amount: Double,
                             timestamp: Instant,
                             isFraud: Boolean = true
                           ): TransactionEvent = {
    TransactionEvent(
      transactionId = txnId,
      domain = "gaming",
      testId = "test-001",
      timestamp = timestamp,
      actorId = actorId,
      amount = amount,
      currency = "USD",
      ipAddress = "192.168.1.1",
      pattern = "fraud_velocity_spike",
      isFraud = isFraud,
      sequencePosition = 1
    )
  }

  behavior of "FraudAlertSink"

  it should "store alert with transactions in the database" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)
    sink.open(null)

    val transactions = Seq(
      createTestTransaction("tx1", "test_actor_123", 500.0, Instant.parse("2025-10-20T10:00:10Z")),
      createTestTransaction("tx2", "test_actor_123", 600.0, Instant.parse("2025-10-20T10:00:20Z")),
      createTestTransaction("tx3", "test_actor_123", 700.0, Instant.parse("2025-10-20T10:00:30Z"))
    )

    val inputAlert: AlertDetail = AlertDetail(  // ✅ Added explicit type
      alertId = 0,
      alertType = "velocity_spike",
      domain = "gaming",
      actorId = "test_actor_123",
      severity = "HIGH",
      fraudScore = 85,
      transactionCount = 3,
      totalAmount = 1800.0,
      firstSeen = Instant.parse("2025-10-20T10:00:00Z"),
      lastSeen = Instant.parse("2025-10-20T10:01:00Z"),
      transactions = transactions,
      modelVersion = "v1.0",
      inferenceTimeMs = 42
    )

    sink.invoke(inputAlert, null.asInstanceOf[SinkFunction.Context])

    val actorIdToQuery = inputAlert.actorId  // ✅ Different variable name

    // Verify alert
    val storedAlert = DB.readOnly { implicit session =>
      sql"""
      SELECT alert_id, alert_type, domain, actor_id, severity,
            fraud_score, transaction_count, total_amount, metadata
      FROM fraud_alerts
      WHERE actor_id = ${actorIdToQuery}
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

    storedAlert should not be empty
    val alert = storedAlert.get

    alert.alertId should be > 0L
    alert.alertType shouldBe "velocity_spike"
    alert.actorId shouldBe "test_actor_123"
    alert.fraudScore shouldBe 85
    alert.transactionCount shouldBe 3

    // Verify transactions
    val transactionCount = DB.readOnly { implicit session =>
      sql"""
      SELECT COUNT(*) as cnt
      FROM suspicious_transactions
      WHERE alert_id = ${alert.alertId}
    """.map(_.int("cnt")).single.apply().get
    }

    transactionCount shouldBe 3

    // Verify transaction details
    val storedTransactions = DB.readOnly { implicit session =>
      sql"""
      SELECT transaction_id, amount, is_fraud
      FROM suspicious_transactions
      WHERE alert_id = ${alert.alertId}
      ORDER BY transaction_id
    """.map { rs =>
        (rs.string("transaction_id"), rs.bigDecimal("amount"), rs.boolean("is_fraud"))
      }.list.apply()
    }

    storedTransactions should have size 3
    storedTransactions.map(_._1) should contain allOf ("tx1", "tx2", "tx3")

    sink.close()

    info(s"✅ Alert ${alert.alertId} with 3 transactions successfully stored")
  }

  it should "store metadata with model info as valid JSON" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)
    sink.open(null)

    val inputAlert: AlertDetail = AlertDetail(  // ✅ Added explicit type
      alertId = 0,
      alertType = "velocity_spike",
      domain = "gaming",
      actorId = "metadata_test",
      severity = "HIGH",
      fraudScore = 85,
      transactionCount = 1,
      totalAmount = 2500.50,
      firstSeen = Instant.parse("2025-10-20T10:00:00Z"),
      lastSeen = Instant.parse("2025-10-20T10:01:00Z"),
      transactions = Seq(
        createTestTransaction("tx1", "metadata_test", 2500.50, Instant.now())
      ),
      modelVersion = "v1.2.3",
      inferenceTimeMs = 123
    )

    sink.invoke(inputAlert, null.asInstanceOf[SinkFunction.Context])

    val actorIdToQuery = inputAlert.actorId  // ✅ Different variable name

    val metadata = DB.readOnly { implicit session =>
      sql"""
      SELECT metadata
      FROM fraud_alerts
      WHERE actor_id = ${actorIdToQuery}
    """.map(_.string("metadata")).single.apply().get
    }

    import com.fasterxml.jackson.databind.ObjectMapper
    import com.fasterxml.jackson.module.scala.DefaultScalaModule

    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    noException should be thrownBy {
      val parsed = mapper.readTree(metadata)

      parsed.has("window_seconds") shouldBe true
      parsed.has("confidence") shouldBe true
      parsed.has("alert_type") shouldBe true
      parsed.has("severity") shouldBe true
      parsed.has("model_version") shouldBe true
      parsed.has("inference_time_ms") shouldBe true

      parsed.get("confidence").asInt() shouldBe 85
      parsed.get("alert_type").asText() shouldBe "velocity_spike"
      parsed.get("model_version").asText() shouldBe "v1.2.3"
      parsed.get("inference_time_ms").asInt() shouldBe 123
    }

    sink.close()

    info("✅ Metadata with model info stored as valid JSON")
  }

  it should "store multiple alerts with their transactions" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)
    sink.open(null)

    val testAlert1 = AlertDetail(
      alertId = 0,
      alertType = "velocity_spike",
      domain = "gaming",
      actorId = "repeat_offender",
      severity = "HIGH",
      fraudScore = 80,
      transactionCount = 2,
      totalAmount = 1000.0,
      firstSeen = Instant.parse("2025-10-20T10:00:00Z"),
      lastSeen = Instant.parse("2025-10-20T10:01:00Z"),
      transactions = Seq(
        createTestTransaction("tx1", "repeat_offender", 500.0, Instant.now()),
        createTestTransaction("tx2", "repeat_offender", 500.0, Instant.now())
      ),
      modelVersion = "v1.0",
      inferenceTimeMs = 42
    )

    val testAlert2 = AlertDetail(
      alertId = 0,
      alertType = "suspicious_pattern",
      domain = "gaming",
      actorId = "repeat_offender",
      severity = "CRITICAL",
      fraudScore = 95,
      transactionCount = 3,
      totalAmount = 5000.0,
      firstSeen = Instant.parse("2025-10-20T11:00:00Z"),
      lastSeen = Instant.parse("2025-10-20T11:01:00Z"),
      transactions = Seq(
        createTestTransaction("tx3", "repeat_offender", 1500.0, Instant.now()),
        createTestTransaction("tx4", "repeat_offender", 2000.0, Instant.now()),
        createTestTransaction("tx5", "repeat_offender", 1500.0, Instant.now())
      ),
      modelVersion = "v1.0",
      inferenceTimeMs = 45
    )

    sink.invoke(testAlert1, null.asInstanceOf[SinkFunction.Context])
    sink.invoke(testAlert2, null.asInstanceOf[SinkFunction.Context])

    val alertCount = DB.readOnly { implicit session =>
      sql"""
        SELECT COUNT(*) as cnt
        FROM fraud_alerts
        WHERE actor_id = 'repeat_offender'
      """.map(_.int("cnt")).single.apply().get
    }

    alertCount shouldBe 2

    val totalTransactions = DB.readOnly { implicit session =>
      sql"""
        SELECT COUNT(*) as cnt
        FROM suspicious_transactions st
        JOIN fraud_alerts fa ON st.alert_id = fa.alert_id
        WHERE fa.actor_id = 'repeat_offender'
      """.map(_.int("cnt")).single.apply().get
    }

    totalTransactions shouldBe 5

    sink.close()

    info("✅ Multiple alerts with transactions stored for same actor")
  }

  it should "store alerts with different severity levels" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)
    sink.open(null)

    val severities = Seq("LOW", "MEDIUM", "HIGH", "CRITICAL")

    severities.zipWithIndex.foreach { case (severity, index) =>
      val testAlert = AlertDetail(
        alertId = 0,
        alertType = "test_pattern",
        domain = "testing",
        actorId = s"actor_$index",
        severity = severity,
        fraudScore = 60 + (index * 10),
        transactionCount = 1,
        totalAmount = (index + 1) * 100.0,
        firstSeen = Instant.now(),
        lastSeen = Instant.now(),
        transactions = Seq(
          createTestTransaction(s"tx$index", s"actor_$index", (index + 1) * 100.0, Instant.now())
        ),
        modelVersion = "v1.0",
        inferenceTimeMs = 42
      )

      sink.invoke(testAlert, null.asInstanceOf[SinkFunction.Context])
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

    sink.close()

    info("✅ All severity levels stored correctly")
  }

  it should "handle large transaction amounts correctly" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)
    sink.open(null)

    val testAlert = AlertDetail(
      alertId = 0,
      alertType = "high_value",
      domain = "finance",
      actorId = "whale_actor",
      severity = "CRITICAL",
      fraudScore = 99,
      transactionCount = 1,
      totalAmount = 9999999.99,
      firstSeen = Instant.now(),
      lastSeen = Instant.now(),
      transactions = Seq(
        createTestTransaction("whale_tx", "whale_actor", 9999999.99, Instant.now())
      ),
      modelVersion = "v1.0",
      inferenceTimeMs = 42
    )

    sink.invoke(testAlert, null.asInstanceOf[SinkFunction.Context])

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

    val testAlert = AlertDetail(
      alertId = 0,
      alertType = "test",
      domain = "test",
      actorId = "test",
      severity = "LOW",
      fraudScore = 50,
      transactionCount = 0,
      totalAmount = 0.0,
      firstSeen = Instant.now(),
      lastSeen = Instant.now(),
      transactions = Seq.empty,
      modelVersion = "v1.0",
      inferenceTimeMs = 42
    )

    an[Exception] should be thrownBy {
      sink.open(null)
      sink.invoke(testAlert, null.asInstanceOf[SinkFunction.Context])
    }

    // ✅ Close the bad sink to clean up
    try {
      sink.close()
    } catch {
      case _: Exception => // Ignore close errors
    }

    info("✅ Handled database unavailability")
  }

  it should "handle alerts with empty transaction list" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)
    sink.open(null)

    val testAlert = AlertDetail(
      alertId = 0,
      alertType = "suspicious_activity",
      domain = "testing",
      actorId = "empty_txn_actor",
      severity = "LOW",
      fraudScore = 60,
      transactionCount = 0,
      totalAmount = 0.0,
      firstSeen = Instant.now(),
      lastSeen = Instant.now(),
      transactions = Seq.empty,
      modelVersion = "v1.0",
      inferenceTimeMs = 42
    )

    noException should be thrownBy {
      sink.invoke(testAlert, null.asInstanceOf[SinkFunction.Context])
    }

    val txnCount = DB.readOnly { implicit session =>
      sql"""
        SELECT COUNT(*) as cnt
        FROM suspicious_transactions st
        JOIN fraud_alerts fa ON st.alert_id = fa.alert_id
        WHERE fa.actor_id = 'empty_txn_actor'
      """.map(_.int("cnt")).single.apply().get
    }

    txnCount shouldBe 0

    sink.close()

    info("✅ Empty transaction list handled correctly")
  }
}