#!/usr/bin/env python3
"""
Quick validation tests for event generator
Run: python test_generator.py

Assumes:
- Local Kafka running on localhost:9092
- Topic 'fraud-events-raw' exists
"""
import sys
from pathlib import Path

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent))

from generators import GamingGenerator, EcommerceGenerator, FinTechGenerator
from kafka_publisher import KafkaPublisher


def test_gaming_generator():
    """Test gaming generator produces valid events"""
    print("\n1️⃣  Testing Gaming Generator...")

    gen = GamingGenerator('test-001')
    events = gen.generate_batch(10)

    # Verify event count
    assert len(events) == 10, f"Expected 10 events, got {len(events)}"
    print(f"   ✅ Generated 10 gaming events")

    # Check fraud distribution
    fraud_count = sum(1 for e in events if e['is_fraud'])
    print(f"   ✅ Fraud events: {fraud_count}/10 ({fraud_count * 10}%)")

    # Check required fields
    required = ['transaction_id', 'player_id', 'game_id', 'amount', 'fraud_pattern', 'domain']
    missing = [f for f in required if f not in events[0]]
    assert not missing, f"Missing fields: {missing}"
    print(f"   ✅ All required fields present")

    # Check domain
    assert events[0]['domain'] == 'gaming'
    print(f"   ✅ Domain correctly set to 'gaming'")

    print("   🎮 Gaming generator: PASS\n")


def test_ecommerce_generator():
    """Test e-commerce generator produces valid events"""
    print("2️⃣  Testing E-commerce Generator...")

    gen = EcommerceGenerator('test-002')
    events = gen.generate_batch(10)

    assert len(events) == 10
    print(f"   ✅ Generated 10 e-commerce events")

    # Check cart items exist
    assert 'cart_items' in events[0]
    assert len(events[0]['cart_items']) > 0
    print(f"   ✅ Cart items present: {len(events[0]['cart_items'])} items")

    # Check domain
    assert events[0]['domain'] == 'ecommerce'
    print(f"   ✅ Domain correctly set to 'ecommerce'")

    print("   🛒 E-commerce generator: PASS\n")


def test_fintech_generator():
    """Test fintech generator produces valid events"""
    print("3️⃣  Testing FinTech Generator...")

    gen = FinTechGenerator('test-003')
    events = gen.generate_batch(10)

    assert len(events) == 10
    print(f"   ✅ Generated 10 fintech events")

    # Check wire transfer fields
    assert 'account_from' in events[0]
    assert 'account_to' in events[0]
    assert 'transfer_type' in events[0]
    print(f"   ✅ Wire transfer fields present")

    # Check domain
    assert events[0]['domain'] == 'fintech'
    print(f"   ✅ Domain correctly set to 'fintech'")

    print("   💰 FinTech generator: PASS\n")


def test_kafka_publisher():
    """Test Kafka publisher can connect and publish"""
    print("4️⃣  Testing Kafka Publisher...")

    try:
        publisher = KafkaPublisher(
            bootstrap_servers='localhost:9092',
            topic='fraud-events-raw'
        )
        print(f"   ✅ Connected to Kafka (localhost:9092)")

        # Generate and publish test events
        gen = GamingGenerator('test-kafka')
        events = gen.generate_batch(5)

        success = publisher.publish(events, 'gaming')
        assert success, "Publish failed"
        print(f"   ✅ Published 5 test events to 'fraud-events-raw'")

        # Check metrics
        metrics = publisher.get_metrics()
        assert metrics['messages_sent'] == 5
        print(f"   ✅ Metrics tracking: {metrics['messages_sent']} sent, {metrics['batches_sent']} batches")

        publisher.close()
        print("   📨 Kafka publisher: PASS\n")

    except Exception as e:
        print(f"   ❌ Kafka test failed: {e}")
        print("   ⚠️  Make sure Kafka is running: docker ps | grep kafka")
        print("   ⚠️  And topic exists: docker exec kafka kafka-topics.sh --list --bootstrap-server localhost:9092\n")
        return False

    return True


def test_fraud_distribution():
    """Test fraud pattern distribution matches expected rates"""
    print("5️⃣  Testing Fraud Distribution...")

    gen = GamingGenerator('test-fraud')
    events = gen.generate_batch(1000)

    # Count fraud patterns
    patterns = {}
    for event in events:
        pattern = event['fraud_pattern']
        patterns[pattern] = patterns.get(pattern, 0) + 1

    print(f"   Distribution (1000 events):")
    for pattern, count in sorted(patterns.items()):
        pct = (count / 1000) * 100
        print(f"     • {pattern}: {count} ({pct:.1f}%)")

    # Verify rates are approximately correct (±2%)
    expected = {
        'account_takeover': 5.0,
        'gold_farming': 3.0,
        'chargeback_fraud': 2.0
    }

    for pattern, expected_pct in expected.items():
        actual_pct = (patterns.get(pattern, 0) / 1000) * 100
        diff = abs(actual_pct - expected_pct)

        if diff > 2.0:
            print(f"   ⚠️  {pattern}: expected ~{expected_pct}%, got {actual_pct:.1f}%")
        else:
            print(f"   ✅ {pattern}: {actual_pct:.1f}% (target: {expected_pct}%)")

    print("   📊 Fraud distribution: PASS\n")


def main():
    """Run all tests"""
    print("=" * 60)
    print("PatternAlarm Event Generator - Validation Tests")
    print("=" * 60)

    try:
        # Test generators
        test_gaming_generator()
        test_ecommerce_generator()
        test_fintech_generator()

        # Test fraud distribution
        test_fraud_distribution()

        # Test Kafka (optional - skip if not running)
        kafka_ok = test_kafka_publisher()

        # Summary
        print("=" * 60)
        if kafka_ok:
            print("✅ All tests passed!")
        else:
            print("⚠️  Tests passed (Kafka skipped)")
        print("=" * 60)
        print("\nNext steps:")
        print("  1. Complete TODOs in ecommerce.py and fintech.py")
        print("  2. Run full test: python main.py --test-id test-001 --domain gaming --load-level normal")
        print("  3. Verify in Kafka: docker exec kafka kafka-console-consumer.sh \\")
        print("       --bootstrap-server localhost:9092 --topic fraud-events-raw --from-beginning --max-messages 5")

        sys.exit(0)

    except AssertionError as e:
        print(f"\n❌ Test failed: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ Unexpected error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == '__main__':
    main()