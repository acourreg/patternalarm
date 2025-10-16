"""
Kafka publisher with configurable bootstrap servers
"""
import os
import json
import logging
from typing import List, Dict, Any
from kafka import KafkaProducer
from kafka.errors import KafkaError

logger = logging.getLogger(__name__)


class KafkaPublisher:
    """Publishes events to Kafka topics"""

    def __init__(self, bootstrap_servers: str = None, topic: str = 'fraud-events-raw'):
        # Single source for env var logic
        self.bootstrap_servers = (
            bootstrap_servers or
            os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'localhost:9092')
        )
        self.topic = topic

        logger.info(f"Connecting to Kafka: {self.bootstrap_servers}")

        self.producer = KafkaProducer(
            bootstrap_servers=self.bootstrap_servers.split(','),
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),
            acks='all',
            retries=3,
            max_in_flight_requests_per_connection=5
        )

    def publish(self, events: List[Dict[str, Any]], domain: str) -> bool:
        """Publish batch of events to Kafka"""
        try:
            for event in events:
                future = self.producer.send(self.topic, value=event)
                future.get(timeout=10)

            self.producer.flush()
            logger.info(f"Published {len(events)} events to {self.topic}")
            return True

        except KafkaError as e:
            logger.error(f"Failed to publish events: {e}")
            return False

    def close(self):
        """Close producer connection"""
        if self.producer:
            self.producer.close()