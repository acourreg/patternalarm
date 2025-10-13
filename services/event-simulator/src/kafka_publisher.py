"""
Simple Kafka publisher for transaction events
"""
import json
import logging
from typing import List, Dict, Any
from kafka import KafkaProducer
from kafka.errors import KafkaError

logger = logging.getLogger(__name__)


class KafkaPublisher:
    """Publishes events to Kafka"""

    def __init__(self, bootstrap_servers: str, topic: str):
        self.topic = topic
        self.producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),
            acks='all',
            retries=3
        )
        logger.info(f"Kafka connected: {bootstrap_servers} → {topic}")

    def publish(self, events: List[Dict[str, Any]], domain: str) -> bool:
        """Publish batch of events to Kafka"""
        try:
            for event in events:
                self.producer.send(self.topic, key=domain.encode('utf-8'), value=event)

            self.producer.flush(timeout=10)
            return True

        except KafkaError as e:
            logger.error(f"Kafka publish failed: {e}")
            return False

    def close(self):
        """Close producer"""
        try:
            self.producer.close(timeout=5)
            logger.info("Kafka publisher closed")
        except Exception as e:
            logger.error(f"Error closing publisher: {e}")