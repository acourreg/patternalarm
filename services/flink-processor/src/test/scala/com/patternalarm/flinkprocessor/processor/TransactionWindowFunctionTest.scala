package com.patternalarm.flinkprocessor.processor

import com.patternalarm.flinkprocessor.model.{TimedWindowAggregate, TransactionEvent}
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.util.Collector
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import scala.collection.JavaConverters.asJavaIterableConverter
import scala.collection.mutable

class TransactionWindowFunctionTest extends AnyFlatSpec with Matchers {

  // Mock Collector to capture output
  class TestCollector[T] extends Collector[T] {
    private val buffer = mutable.ListBuffer[T]()

    override def collect(record: T): Unit = buffer += record
    override def close(): Unit = ()

    def getResults: List[T] = buffer.toList
  }

  // Helper to create test events
  def createEvent(
                   txnId: String,
                   actorId: String,
                   amount: Double,
                   timestamp: Instant,
                   domain: String = "gaming"
                 ): TransactionEvent = {
    TransactionEvent(
      transactionId = txnId,
      domain = domain,
      testId = "test-001",
      timestamp = timestamp,
      actorId = actorId,
      amount = amount,
      currency = "USD",
      ipAddress = "192.168.1.1",
      pattern = "regular_pattern",
      isFraud = false,
      sequencePosition = 0
    )
  }

  "TransactionWindowFunction" should "aggregate single transaction correctly" in {
    val function = new TransactionWindowFunction()
    val collector = new TestCollector[TimedWindowAggregate]()

    val windowStart = Instant.parse("2025-10-16T18:00:00Z")
    val windowEnd = Instant.parse("2025-10-16T18:01:00Z")
    val window = new TimeWindow(windowStart.toEpochMilli, windowEnd.toEpochMilli)

    val event = createEvent("TXN001", "A123", 150.0, windowStart.plusSeconds(15))
    val events = Iterable(event).asJava

    function.apply("A123", window, events, collector)

    val results = collector.getResults
    results should have size 1

    val aggregate = results.head
    aggregate.actorId shouldBe "A123"
    aggregate.domain shouldBe "gaming"
    aggregate.transactionCount shouldBe 1
    aggregate.totalAmount shouldBe 150.0
    aggregate.windowStart shouldBe windowStart
    aggregate.windowEnd shouldBe windowEnd
    aggregate.transactions should have size 1
    aggregate.transactions.head.transactionId shouldBe "TXN001"
  }

  it should "aggregate multiple transactions correctly" in {
    val function = new TransactionWindowFunction()
    val collector = new TestCollector[TimedWindowAggregate]()

    val windowStart = Instant.parse("2025-10-16T18:00:00Z")
    val windowEnd = Instant.parse("2025-10-16T18:01:00Z")
    val window = new TimeWindow(windowStart.toEpochMilli, windowEnd.toEpochMilli)

    val events = List(
      createEvent("TXN001", "A123", 100.0, windowStart.plusSeconds(10)),
      createEvent("TXN002", "A123", 200.0, windowStart.plusSeconds(20)),
      createEvent("TXN003", "A123", 50.0, windowStart.plusSeconds(30))
    )

    function.apply("A123", window, events.asJava, collector)

    val results = collector.getResults
    results should have size 1

    val aggregate = results.head
    aggregate.actorId shouldBe "A123"
    aggregate.transactionCount shouldBe 3
    aggregate.totalAmount shouldBe 350.0
    aggregate.transactions should have size 3
  }

  it should "calculate correct window boundaries" in {
    val function = new TransactionWindowFunction()
    val collector = new TestCollector[TimedWindowAggregate]()

    val windowStart = Instant.parse("2025-10-16T18:00:00Z")
    val windowEnd = Instant.parse("2025-10-16T18:01:00Z")
    val window = new TimeWindow(windowStart.toEpochMilli, windowEnd.toEpochMilli)

    val event = createEvent("TXN001", "A123", 100.0, windowStart.plusSeconds(30))

    function.apply("A123", window, List(event).asJava, collector)

    val aggregate = collector.getResults.head
    aggregate.windowStart shouldBe windowStart
    aggregate.windowEnd shouldBe windowEnd
  }

  it should "preserve all transaction details" in {
    val function = new TransactionWindowFunction()
    val collector = new TestCollector[TimedWindowAggregate]()

    val windowStart = Instant.parse("2025-10-16T18:00:00Z")
    val windowEnd = Instant.parse("2025-10-16T18:01:00Z")
    val window = new TimeWindow(windowStart.toEpochMilli, windowEnd.toEpochMilli)

    val event = TransactionEvent(
      transactionId = "TXN001",
      domain = "fintech",
      testId = "test-002",
      timestamp = windowStart.plusSeconds(15),
      actorId = "A456",
      amount = 250.0,
      currency = "EUR",
      ipAddress = "10.0.0.1",
      pattern = "fraud_velocity_spike",
      isFraud = true,
      sequencePosition = 2,
      accountFrom = Some("ACC123"),
      accountTo = Some("ACC456")
    )

    function.apply("A456", window, List(event).asJava, collector)

    val aggregate = collector.getResults.head
    val preservedEvent = aggregate.transactions.head

    preservedEvent.transactionId shouldBe "TXN001"
    preservedEvent.domain shouldBe "fintech"
    preservedEvent.currency shouldBe "EUR"
    preservedEvent.pattern shouldBe "fraud_velocity_spike"
    preservedEvent.isFraud shouldBe true
    preservedEvent.accountFrom shouldBe Some("ACC123")
    preservedEvent.accountTo shouldBe Some("ACC456")
  }

  it should "handle empty window gracefully" in {
    val function = new TransactionWindowFunction()
    val collector = new TestCollector[TimedWindowAggregate]()

    val windowStart = Instant.parse("2025-10-16T18:00:00Z")
    val windowEnd = Instant.parse("2025-10-16T18:01:00Z")
    val window = new TimeWindow(windowStart.toEpochMilli, windowEnd.toEpochMilli)

    function.apply("A123", window, List.empty.asJava, collector)

    val results = collector.getResults
    results shouldBe empty
  }

  it should "aggregate transactions from different domains separately" in {
    val function = new TransactionWindowFunction()

    val windowStart = Instant.parse("2025-10-16T18:00:00Z")
    val windowEnd = Instant.parse("2025-10-16T18:01:00Z")
    val window = new TimeWindow(windowStart.toEpochMilli, windowEnd.toEpochMilli)

    // Gaming transactions
    val gamingCollector = new TestCollector[TimedWindowAggregate]()
    val gamingEvents = List(
      createEvent("TXN001", "A123", 50.0, windowStart.plusSeconds(10), "gaming"),
      createEvent("TXN002", "A123", 75.0, windowStart.plusSeconds(20), "gaming")
    )
    function.apply("A123", window, gamingEvents.asJava, gamingCollector)

    val gamingAggregate = gamingCollector.getResults.head
    gamingAggregate.domain shouldBe "gaming"
    gamingAggregate.totalAmount shouldBe 125.0

    // Fintech transactions
    val fintechCollector = new TestCollector[TimedWindowAggregate]()
    val fintechEvents = List(
      createEvent("TXN003", "A456", 1000.0, windowStart.plusSeconds(15), "fintech")
    )
    function.apply("A456", window, fintechEvents.asJava, fintechCollector)

    val fintechAggregate = fintechCollector.getResults.head
    fintechAggregate.domain shouldBe "fintech"
    fintechAggregate.totalAmount shouldBe 1000.0
  }

  it should "sum amounts correctly with floating point precision" in {
    val function = new TransactionWindowFunction()
    val collector = new TestCollector[TimedWindowAggregate]()

    val windowStart = Instant.parse("2025-10-16T18:00:00Z")
    val windowEnd = Instant.parse("2025-10-16T18:01:00Z")
    val window = new TimeWindow(windowStart.toEpochMilli, windowEnd.toEpochMilli)

    val events = List(
      createEvent("TXN001", "A123", 10.50, windowStart.plusSeconds(10)),
      createEvent("TXN002", "A123", 20.25, windowStart.plusSeconds(20)),
      createEvent("TXN003", "A123", 5.75, windowStart.plusSeconds(30))
    )

    function.apply("A123", window, events.asJava, collector)

    val aggregate = collector.getResults.head
    aggregate.totalAmount shouldBe 36.5 +- 0.01  // Floating point tolerance
  }

  it should "maintain transaction order in list" in {
    val function = new TransactionWindowFunction()
    val collector = new TestCollector[TimedWindowAggregate]()

    val windowStart = Instant.parse("2025-10-16T18:00:00Z")
    val windowEnd = Instant.parse("2025-10-16T18:01:00Z")
    val window = new TimeWindow(windowStart.toEpochMilli, windowEnd.toEpochMilli)

    val events = List(
      createEvent("TXN001", "A123", 100.0, windowStart.plusSeconds(10)),
      createEvent("TXN002", "A123", 200.0, windowStart.plusSeconds(20)),
      createEvent("TXN003", "A123", 300.0, windowStart.plusSeconds(30))
    )

    function.apply("A123", window, events.asJava, collector)

    val aggregate = collector.getResults.head
    val txnIds = aggregate.transactions.map(_.transactionId)
    txnIds shouldBe List("TXN001", "TXN002", "TXN003")
  }
}