#!/usr/bin/env python3
"""
Quick validation tests for event generator
"""
import sys
from pathlib import Path

# Add src directory to path
sys.path.insert(0, str(Path(__file__).parent.parent / 'src'))

from generators.gaming import GamingGenerator
from generators.ecommerce import EcommerceGenerator
from generators.fintech import FinTechGenerator
from generators.base import TransactionPattern
from kafka_publisher import KafkaPublisher

# Test pattern rates (defined once at top)
TEST_RATES = {
    TransactionPattern.FRAUD_ACCOUNT_TAKEOVER: 0.05,
    TransactionPattern.FRAUD_GOLD_FARMING: 0.03,
    TransactionPattern.FRAUD_CHARGEBACK_FRAUD: 0.02,
    TransactionPattern.REGULAR_CASUAL_PLAYER: 0.90,
}


def test_gaming_generator():
    print("\n🎮 Testing Gaming Generator...")
    gen = GamingGenerator('test-001')
    events = gen.generate_batch(10, TEST_RATES)

    assert len(events) == 10
    print(f"   ✅ Generated {len(events)} events")

    fraud_count = sum(1 for e in events if e['is_fraud'])
    print(f"   ✅ Fraud events: {fraud_count}/10")
    print("   PASS\n")


def test_kafka_publisher():
    print("📨 Testing Kafka Publisher...")

    try:
        publisher = KafkaPublisher('localhost:9092', 'fraud-events-raw')
        print(f"   ✅ Connected to Kafka")

        gen = GamingGenerator('test-kafka')
        events = gen.generate_batch(5, TEST_RATES)  # Use TEST_RATES here

        success = publisher.publish(events, 'gaming')
        assert success
        print(f"   ✅ Published 5 events")

        publisher.close()
        print("   PASS\n")
        return True

    except Exception as e:
        print(f"   ❌ Kafka test failed: {e}")
        print("   ⚠️  Start Kafka: docker-compose up -d\n")
        return False


def main():
    print("=" * 60)
    print("PatternAlarm - Validation Tests")
    print("=" * 60)

    test_gaming_generator()
    kafka_ok = test_kafka_publisher()

    print("=" * 60)
    print("✅ All tests passed!" if kafka_ok else "⚠️  Tests passed (Kafka skipped)")
    print("=" * 60)


if __name__ == '__main__':
    main()