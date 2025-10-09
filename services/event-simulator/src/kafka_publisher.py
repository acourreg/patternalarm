"""
Kafka publisher with batching and backpressure handling
AC2.4: Batch size 1000 or 1 second, handle consumer lag >10s

Production risks:
- Kafka broker down: Messages accumulate in memory (OOM risk)
- Network partition: Silent message loss if acks=1
- Consumer lag: Need to throttle producer to prevent broker overwhelm
"""
import json
import time
import logging
from typing import List, Dict, Any, Optional
from dataclasses import dataclass
from kafka import KafkaProducer, KafkaConsumer, KafkaAdminClient
from kafka.errors import KafkaError, KafkaTimeoutError
from kafka.admin import ConsumerGroupDescription

logger = logging.getLogger(__name__)


@dataclass
class PublisherMetrics:
    """Track publisher performance"""
    messages_sent: int = 0
    batches_sent: int = 0
    failures: int = 0
    total_bytes: int = 0
    lag_throttles: int = 0


class KafkaPublisher:
    """
    Publishes events to Kafka with batching and backpressure
    """

    def __init__(self,
                 bootstrap_servers: str = 'localhost:9092',
                 topic: str = 'fraud-events-raw',
                 batch_size: int = 1000,
                 batch_timeout_seconds: float = 1.0,
                 max_consumer_lag_seconds: int = 10):
        """
        Args:
            bootstrap_servers: Kafka broker addresses
            topic: Topic to publish to
            batch_size: Max events before forcing flush
            batch_timeout_seconds: Max time before forcing flush
            max_consumer_lag_seconds: Throttle if consumer lag exceeds this
        """
        self.topic = topic
        self.batch_size = batch_size
        self.batch_timeout = batch_timeout_seconds
        self.max_lag = max_consumer_lag_seconds

        # Initialize producer with reliability settings
        self.producer = self._create_producer(bootstrap_servers)
        self.admin_client = KafkaAdminClient(bootstrap_servers=bootstrap_servers)

        # Batching state
        self.current_batch = []
        self.last_flush_time = time.time()

        # Metrics
        self.metrics = PublisherMetrics()

        logger.info(f"KafkaPublisher initialized: topic={topic}, batch_size={batch_size}")

    def _create_producer(self, bootstrap_servers: str) -> KafkaProducer:
        """
        Create producer with production-ready settings

        Production note: These settings favor reliability over throughput
        - acks='all': Wait for all replicas (slower but safer)
        - retries=3: Retry transient failures
        - compression: Save bandwidth
        """
        try:
            return KafkaProducer(
                bootstrap_servers=bootstrap_servers,
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                key_serializer=lambda k: k.encode('utf-8') if k else None,
                acks='all',  # Wait for all replicas to acknowledge
                retries=3,
                max_in_flight_requests_per_connection=5,
                compression_type='snappy',
                batch_size=16384,  # Kafka internal batching (16KB)
                linger_ms=10,  # Small delay for batching
                request_timeout_ms=30000
            )
        except KafkaError as e:
            logger.error(f"Failed to create Kafka producer: {e}")
            raise

    def publish(self, events: List[Dict[str, Any]], domain: str) -> bool:
        """
        Publish events with batching and backpressure

        Returns: True if all events published successfully

        Production risk: If consumer lag check fails, we skip throttling
        but continue publishing (fail-open). Better than blocking producer.
        """
        try:
            # Check consumer lag before publishing large batch
            if len(events) > 100:
                self._handle_backpressure()

            for event in events:
                self.current_batch.append(event)

                # Flush if batch size reached
                if len(self.current_batch) >= self.batch_size:
                    self._flush_batch(domain)

                # Flush if timeout reached
                elif time.time() - self.last_flush_time >= self.batch_timeout:
                    self._flush_batch(domain)

            # Final flush
            if self.current_batch:
                self._flush_batch(domain)

            return True

        except Exception as e:
            logger.error(f"Publish failed: {e}")
            self.metrics.failures += len(events)
            return False

    def _flush_batch(self, domain: str):
        """Send accumulated batch to Kafka"""
        if not self.current_batch:
            return

        batch_start = time.time()

        try:
            # Send each event in batch
            for event in self.current_batch:
                # Use domain as partition key for balanced distribution
                future = self.producer.send(
                    self.topic,
                    key=domain,
                    value=event
                )

                # Don't wait for individual acks (handled by producer settings)

            # Force immediate send
            self.producer.flush(timeout=5)

            # Update metrics
            batch_count = len(self.current_batch)
            self.metrics.messages_sent += batch_count
            self.metrics.batches_sent += 1

            batch_duration = time.time() - batch_start
            logger.debug(f"Flushed batch: {batch_count} events in {batch_duration:.2f}s")

        except KafkaTimeoutError as e:
            logger.error(f"Kafka timeout during flush: {e}")
            self.metrics.failures += len(self.current_batch)
            raise

        finally:
            # Clear batch regardless of success/failure
            self.current_batch = []
            self.last_flush_time = time.time()

    def _handle_backpressure(self):
        """
        Check consumer lag and throttle if needed

        Production risk: Consumer group may not exist yet on first run.
        We log warning but don't block (fail-open).
        """
        try:
            # This is a simplified check - real implementation would:
            # 1. Query consumer group lag via AdminClient
            # 2. Check if lag_seconds > threshold
            # 3. Sleep if needed

            # For local development with Docker Kafka, consumer might not exist yet
            # In Phase 2, we'll properly query MSK consumer group metrics

            # Placeholder: In production, query actual lag
            # groups = self.admin_client.describe_consumer_groups(['PatternAlarm-flink-processor'])
            # lag_seconds = self._calculate_lag(groups)

            # if lag_seconds > self.max_lag:
            #     throttle_time = min(lag_seconds - self.max_lag, 5)  # Max 5s throttle
            #     logger.warning(f"Consumer lag {lag_seconds}s > {self.max_lag}s, throttling {throttle_time}s")
            #     time.sleep(throttle_time)
            #     self.metrics.lag_throttles += 1

            pass  # Skip lag check for Phase 1 local development

        except Exception as e:
            # Don't block on monitoring failures
            logger.warning(f"Backpressure check failed (non-fatal): {e}")

    def get_metrics(self) -> Dict[str, Any]:
        """Return publisher metrics"""
        return {
            'messages_sent': self.metrics.messages_sent,
            'batches_sent': self.metrics.batches_sent,
            'failures': self.metrics.failures,
            'lag_throttles': self.metrics.lag_throttles,
            'avg_batch_size': (
                self.metrics.messages_sent / self.metrics.batches_sent
                if self.metrics.batches_sent > 0 else 0
            )
        }

    def close(self):
        """Flush remaining events and close producer"""
        try:
            if self.current_batch:
                self._flush_batch('unknown')
            self.producer.flush(timeout=10)
            self.producer.close(timeout=10)
            logger.info(f"KafkaPublisher closed. Final metrics: {self.get_metrics()}")
        except Exception as e:
            logger.error(f"Error closing publisher: {e}")