package com.patternalarm.flinkprocessor.sink

import com.patternalarm.flinkprocessor.model.{Alert, TransactionEvent}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalikejdbc._

import java.time.Instant

class FraudAlertSinkTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  private val jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
  private val username = "sa"
  private val password = ""

  override def beforeAll(): Unit = {
    Class.forName("org.h2.Driver")
    ConnectionPool.singleton(jdbcUrl, username, password)

    DB.autoCommit { implicit session =>
      sql"""
      CREATE TABLE fraud_alerts (
        alert_id BIGINT AUTO_INCREMENT PRIMARY KEY,
        alert_type VARCHAR(50) NOT NULL,
        domain VARCHAR(20) NOT NULL,
        actor_id VARCHAR(100) NOT NULL,
        severity VARCHAR(20) NOT NULL,
        fraud_score INT NOT NULL,
        transaction_count INT NOT NULL,
        total_amount DECIMAL(15,2) NOT NULL,
        first_seen TIMESTAMP NOT NULL,
        last_seen TIMESTAMP NOT NULL,
        window_seconds BIGINT,
        baseline_avg DECIMAL(15,2),
        patterns_detected VARCHAR(1000),
        confidence INT,
        model_version VARCHAR(50),
        inference_time_ms INT,
        player_id VARCHAR(100),
        game_id VARCHAR(100),
        item_type VARCHAR(50),
        item_name VARCHAR(100),
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
        payment_method VARCHAR(50),
        device_id VARCHAR(100),
        ip_address VARCHAR(50),
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
    """.execute.apply()

      sql"""
      CREATE TABLE suspicious_transactions (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        alert_id BIGINT NOT NULL,
        transaction_id VARCHAR(100) NOT NULL,
        domain VARCHAR(20) NOT NULL,
        test_id VARCHAR(100) NOT NULL,
        timestamp TIMESTAMP NOT NULL,
        actor_id VARCHAR(100) NOT NULL,
        amount DECIMAL(15,2) NOT NULL,
        currency VARCHAR(10) NOT NULL,
        ip_address VARCHAR(50) NOT NULL,
        pattern VARCHAR(50) NOT NULL,
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
        FOREIGN KEY (alert_id) REFERENCES fraud_alerts(alert_id) ON DELETE CASCADE
      )
    """.execute.apply()
    }

    println("\n✅ H2 in-memory database initialized\n")
  }

  override def afterEach(): Unit = {
    try {
      DB.autoCommit { implicit session =>
        sql"DELETE FROM suspicious_transactions".execute.apply()
        sql"DELETE FROM fraud_alerts".execute.apply()
      }
    } catch {
      case _: Exception =>
      // Ignore cleanup errors
    }
  }

  override def afterAll(): Unit = {
    ConnectionPool.closeAll()
    println("\n🔌 H2 connection pool closed\n")
  }

  behavior of "FraudAlertSink"

  it should "store an alert with transactions successfully" in {
    // ✅ Don't call sink.open() - connection pool already exists
    val sink = new FraudAlertSink(jdbcUrl, username, password)

    val now = Instant.now()
    val alert = Alert(
      alertId = 0,
      alertType = "velocity_spike",
      domain = "gaming",
      actorId = "user123",
      severity = "HIGH",
      fraudScore = 85,
      transactionCount = 3,
      totalAmount = 1500.0,
      firstSeen = now,
      lastSeen = now.plusSeconds(60),
      windowSeconds = Some(60L),
      modelVersion = Some("v1.0"),
      inferenceTimeMs = Some(42)
    )

    val transactions = Seq(
      createTransaction("tx1", "user123", "gaming", 500.0, now),
      createTransaction("tx2", "user123", "gaming", 500.0, now.plusSeconds(30)),
      createTransaction("tx3", "user123", "gaming", 500.0, now.plusSeconds(60))
    )

    sink.invoke((alert, transactions), null)

    DB.readOnly { implicit session =>
      val alertCount = sql"SELECT COUNT(*) FROM fraud_alerts".map(_.int(1)).single.apply().get
      val txnCount = sql"SELECT COUNT(*) FROM suspicious_transactions".map(_.int(1)).single.apply().get

      alertCount shouldBe 1
      txnCount shouldBe 3
    }

    println("✅ Alert 1 with 3 transactions successfully stored\n")
  }

  it should "store metadata fields correctly" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)

    val now = Instant.now()
    val alert = Alert(
      alertId = 0,
      alertType = "velocity_spike",
      domain = "gaming",
      actorId = "user456",
      severity = "CRITICAL",
      fraudScore = 95,
      transactionCount = 5,
      totalAmount = 2500.0,
      firstSeen = now,
      lastSeen = now.plusSeconds(120),
      windowSeconds = Some(120L),
      baselineAvg = Some(500.0),
      confidence = Some(95),
      modelVersion = Some("v2.0"),
      inferenceTimeMs = Some(25)
    )

    sink.invoke((alert, Seq.empty), null)

    DB.readOnly { implicit session =>
      val result = sql"""
        SELECT window_seconds, baseline_avg, confidence, model_version, inference_time_ms
        FROM fraud_alerts
        WHERE actor_id = 'user456'
      """.map { rs =>
        (
          rs.longOpt("window_seconds"),
          rs.doubleOpt("baseline_avg"),
          rs.intOpt("confidence"),
          rs.stringOpt("model_version"),
          rs.intOpt("inference_time_ms")
        )
      }.single.apply()

      result shouldBe defined
      val (windowSec, baseline, conf, model, inference) = result.get

      windowSec shouldBe Some(120L)
      baseline shouldBe Some(500.0)
      conf shouldBe Some(95)
      model shouldBe Some("v2.0")
      inference shouldBe Some(25)
    }

    println("✅ Metadata with model info stored correctly\n")
  }

  it should "store multiple alerts for the same actor" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)

    val now = Instant.now()
    val alert1 = Alert(
      alertId = 0,
      alertType = "velocity_spike",
      domain = "gaming",
      actorId = "repeat_offender",
      severity = "HIGH",
      fraudScore = 80,
      transactionCount = 2,
      totalAmount = 1000.0,
      firstSeen = now,
      lastSeen = now.plusSeconds(30)
    )

    val alert2 = Alert(
      alertId = 0,
      alertType = "account_takeover",
      domain = "gaming",
      actorId = "repeat_offender",
      severity = "CRITICAL",
      fraudScore = 95,
      transactionCount = 3,
      totalAmount = 1500.0,
      firstSeen = now.plusSeconds(60),
      lastSeen = now.plusSeconds(120)
    )

    val txns1 = Seq(
      createTransaction("tx1", "repeat_offender", "gaming", 500.0, now),
      createTransaction("tx2", "repeat_offender", "gaming", 500.0, now.plusSeconds(30))
    )

    val txns2 = Seq(
      createTransaction("tx3", "repeat_offender", "gaming", 500.0, now.plusSeconds(60)),
      createTransaction("tx4", "repeat_offender", "gaming", 500.0, now.plusSeconds(90)),
      createTransaction("tx5", "repeat_offender", "gaming", 500.0, now.plusSeconds(120))
    )

    sink.invoke((alert1, txns1), null)
    sink.invoke((alert2, txns2), null)

    DB.readOnly { implicit session =>
      val alertCount = sql"""
        SELECT COUNT(*) FROM fraud_alerts WHERE actor_id = 'repeat_offender'
      """.map(_.int(1)).single.apply().get

      val txnCount = sql"""
        SELECT COUNT(*) FROM suspicious_transactions
        WHERE actor_id = 'repeat_offender'
      """.map(_.int(1)).single.apply().get

      alertCount shouldBe 2
      txnCount shouldBe 5
    }

    println("✅ Multiple alerts with transactions stored for same actor\n")
  }

  it should "store alerts with different severity levels" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)

    val now = Instant.now()
    val severities = Seq("LOW", "MEDIUM", "HIGH", "CRITICAL")

    severities.zipWithIndex.foreach { case (severity, idx) =>
      val alert = Alert(
        alertId = 0,
        alertType = "test",
        domain = "gaming",
        actorId = s"user_$severity",
        severity = severity,
        fraudScore = 50 + (idx * 10),
        transactionCount = 1,
        totalAmount = 100.0,
        firstSeen = now,
        lastSeen = now.plusSeconds(10)
      )
      sink.invoke((alert, Seq.empty), null)
    }

    DB.readOnly { implicit session =>
      severities.foreach { severity =>
        val exists = sql"""
          SELECT COUNT(*) FROM fraud_alerts
          WHERE severity = $severity
        """.map(_.int(1)).single.apply().get

        exists shouldBe 1
      }
    }

    println("✅ All severity levels stored correctly\n")
  }

  it should "handle large transaction amounts correctly" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)

    val now = Instant.now()
    val alert = Alert(
      alertId = 0,
      alertType = "large_amount",
      domain = "fintech",
      actorId = "whale_user",
      severity = "HIGH",
      fraudScore = 75,
      transactionCount = 1,
      totalAmount = 999999.99,
      firstSeen = now,
      lastSeen = now
    )

    val transaction = createTransaction("tx_large", "whale_user", "fintech", 999999.99, now)

    sink.invoke((alert, Seq(transaction)), null)

    DB.readOnly { implicit session =>
      val amount = sql"""
        SELECT total_amount FROM fraud_alerts WHERE actor_id = 'whale_user'
      """.map(_.double("total_amount")).single.apply()

      amount shouldBe Some(999999.99)
    }

    println("✅ Large amounts handled correctly\n")
  }

  it should "handle empty transaction list" in {
    val sink = new FraudAlertSink(jdbcUrl, username, password)

    val now = Instant.now()
    val alert = Alert(
      alertId = 0,
      alertType = "suspicious",
      domain = "gaming",
      actorId = "no_txns_user",
      severity = "MEDIUM",
      fraudScore = 65,
      transactionCount = 0,
      totalAmount = 0.0,
      firstSeen = now,
      lastSeen = now
    )

    sink.invoke((alert, Seq.empty), null)

    DB.readOnly { implicit session =>
      val alertCount = sql"""
        SELECT COUNT(*) FROM fraud_alerts WHERE actor_id = 'no_txns_user'
      """.map(_.int(1)).single.apply().get

      val txnCount = sql"""
        SELECT COUNT(*) FROM suspicious_transactions
        WHERE alert_id = (SELECT alert_id FROM fraud_alerts WHERE actor_id = 'no_txns_user')
      """.map(_.int(1)).single.apply().get

      alertCount shouldBe 1
      txnCount shouldBe 0
    }

    println("✅ Empty transaction list handled correctly\n")
  }

  it should "fail gracefully when database connection fails" in {
    val badJdbcUrl = "jdbc:h2:mem:nonexistent;IFEXISTS=TRUE"
    val sink = new FraudAlertSink(badJdbcUrl, username, password)

    val now = Instant.now()
    val testAlert = Alert(
      alertId = 0,
      alertType = "test",
      domain = "test",
      actorId = "test",
      severity = "LOW",
      fraudScore = 50,
      transactionCount = 0,
      totalAmount = 0.0,
      firstSeen = now,
      lastSeen = now
    )

    an[Exception] should be thrownBy {
      sink.open(null)  // This will create its own bad connection pool
      sink.invoke((testAlert, Seq.empty), null)
    }

    try {
      sink.close()
    } catch {
      case _: Exception => // Ignore close errors
    }

    // ✅ Reinitialize the good connection pool for any remaining tests
    ConnectionPool.singleton(jdbcUrl, username, password)

    println("✅ Handled database unavailability\n")
  }

  // Helper method
  private def createTransaction(
                                 id: String,
                                 actorId: String,
                                 domain: String,
                                 amount: Double,
                                 timestamp: Instant
                               ): TransactionEvent = {
    TransactionEvent(
      transactionId = id,
      domain = domain,
      testId = "test",
      timestamp = timestamp,
      actorId = actorId,
      amount = amount,
      currency = "USD",
      ipAddress = "127.0.0.1",
      pattern = "test_pattern",
      isFraud = true,
      sequencePosition = 1
    )
  }
}