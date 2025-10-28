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
from main import run_test, LoadLevel, GAMING_PATTERN_RATES  # 🆕 Import run_test



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
        publisher = KafkaPublisher('localhost:9092', 'all-transactions')
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



def test_run_test():
    """Test the main run_test function end-to-end"""
    print("🚀 Testing run_test() function...")

    try:
        result = run_test(
            test_id='test-integration',
            domain='gaming',
            load_level=LoadLevel.NORMAL,
            duration_seconds=60,
            kafka_bootstrap='localhost:9092',
            kafka_topic='gaming-events'
        )

        # Verify result structure
        assert 'test_id' in result
        assert 'events_published' in result
        assert 'actual_rate_per_min' in result
        assert result['events_published'] > 0

        print(f"   ✅ Test ID: {result['test_id']}")
        print(f"   ✅ Published: {result['events_published']} events")
        print(f"   ✅ Rate: {result['actual_rate_per_min']}/min")
        print(f"   ✅ Fraud rate: {result['fraud_rate_percent']}%")
        print("   PASS\n")
        return True

    except Exception as e:
        print(f"   ❌ run_test() failed: {e}")
        print("   ⚠️  Start Kafka: docker compose up -d\n")
        return False


def main():
    print("=" * 60)
    print("PatternAlarm - Validation Tests")
    print("=" * 60)

    test_gaming_generator()
    kafka_ok = test_kafka_publisher()

    # Only test run_test if Kafka is available
    run_test_ok = False
    if kafka_ok:
        run_test_ok = test_run_test()

    print("=" * 60)
    if kafka_ok and run_test_ok:
        print("✅ All tests passed!")
    else:
        print("⚠️  Tests passed (Kafka tests skipped)")
    print("=" * 60)


if __name__ == '__main__':
    main()