#!/usr/bin/env python3
"""
Event generator CLI - invoked by curl or Spring Boot
Usage: python main.py --test-id test-001 --domain gaming --load-level normal --duration 60
"""
import sys
import json
import logging
import argparse
import time
from pathlib import Path
from enum import Enum

# Add src to path for imports
sys.path.insert(0, str(Path(__file__).parent))

from generators.gaming import GamingGenerator
from generators.ecommerce import EcommerceGenerator
from generators.fintech import FinTechGenerator
from kafka_publisher import KafkaPublisher


class LoadLevel(Enum):
    """Load testing profiles"""
    NORMAL = ('normal', 10_000)  # 10K events/min
    PEAK = ('peak', 50_000)  # 50K events/min
    CRISIS = ('crisis', 100_000)  # 100K events/min

    def __init__(self, name: str, events_per_min: int):
        self.level_name = name
        self.events_per_min = events_per_min

    @classmethod
    def from_string(cls, level: str):
        for load in cls:
            if load.level_name == level.lower():
                return load
        raise ValueError(f"Unknown load level: {level}")


def setup_logging(verbose: bool = False):
    """Configure logging"""
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        format='%(asctime)s [%(levelname)s] %(message)s',
        level=level,
        datefmt='%Y-%m-%d %H:%M:%S'
    )


def create_generator(domain: str, test_id: str):
    """Factory for domain-specific generator"""
    generators = {
        'gaming': GamingGenerator,
        'ecommerce': EcommerceGenerator,
        'fintech': FinTechGenerator
    }

    generator_class = generators.get(domain)
    if not generator_class:
        raise ValueError(f"Unknown domain: {domain}")

    return generator_class(test_id)


def run_test(test_id: str, domain: str, load_level: LoadLevel,
             duration_seconds: int, kafka_bootstrap: str, kafka_topic: str):
    """
    Execute load test - AC2.5 simplified (no multiprocessing yet)
    Phase 1: Serial batch generation
    Phase 2: Replace with Lambda concurrency
    """
    logger = logging.getLogger(__name__)

    logger.info(f"Starting test: {test_id}")
    logger.info(f"Domain: {domain}, Load: {load_level.level_name}, Duration: {duration_seconds}s")
    logger.info(f"Target: {load_level.events_per_min:,} events/min")

    # Create generator and publisher
    generator = create_generator(domain, test_id)
    publisher = KafkaPublisher(
        bootstrap_servers=kafka_bootstrap,
        topic=kafka_topic
    )

    # Calculate batching
    target_events = load_level.events_per_min
    batch_size = 1000  # AC2.4: batch size
    total_batches = target_events // batch_size

    # Metrics
    start_time = time.time()
    events_generated = 0
    events_published = 0
    errors = 0

    try:
        # Generate events in batches
        logger.info(f"Generating {total_batches} batches of {batch_size} events...")

        for batch_num in range(total_batches):
            # Generate batch
            events = generator.generate_batch(batch_size)
            events_generated += len(events)

            # Publish to Kafka
            success = publisher.publish(events, domain)
            if success:
                events_published += len(events)
            else:
                errors += len(events)

            # Progress logging
            if (batch_num + 1) % 10 == 0:
                elapsed = time.time() - start_time
                rate = (events_published / elapsed * 60) if elapsed > 0 else 0
                logger.info(f"Progress: {events_published:,} published ({rate:.0f}/min)")

        # Final metrics
        end_time = time.time()
        duration = end_time - start_time
        actual_rate = (events_published / duration * 60) if duration > 0 else 0
        error_rate = (errors / events_generated * 100) if events_generated > 0 else 0

        # Build result
        result = {
            'test_id': test_id,
            'domain': domain,
            'load_level': load_level.level_name,
            'duration_seconds': round(duration, 2),
            'events_generated': events_generated,
            'events_published': events_published,
            'errors': errors,
            'error_rate_percent': round(error_rate, 2),
            'target_rate_per_min': load_level.events_per_min,
            'actual_rate_per_min': round(actual_rate, 0),
            'efficiency_percent': round((actual_rate / load_level.events_per_min * 100), 1)
        }

        logger.info("=" * 60)
        logger.info(f"Test Complete: {test_id}")
        logger.info(f"Published: {events_published:,} events in {duration:.1f}s")
        logger.info(f"Rate: {actual_rate:,.0f}/min (target: {load_level.events_per_min:,}/min)")
        logger.info(f"Errors: {errors} ({error_rate:.2f}%)")
        logger.info("=" * 60)

        return result

    finally:
        publisher.close()


def parse_args():
    """Parse command line arguments"""
    parser = argparse.ArgumentParser(description='PatternAlarm Event Generator')

    parser.add_argument('--test-id', required=True, help='Unique test identifier')
    parser.add_argument('--domain', required=True,
                        choices=['gaming', 'ecommerce', 'fintech'],
                        help='Transaction domain')
    parser.add_argument('--load-level', required=True,
                        choices=['normal', 'peak', 'crisis'],
                        help='Load testing profile')
    parser.add_argument('--duration', type=int, default=60,
                        help='Test duration in seconds (unused in Phase 1)')
    parser.add_argument('--kafka-bootstrap', default='localhost:9092',
                        help='Kafka bootstrap servers')
    parser.add_argument('--kafka-topic', default='fraud-events-raw',
                        help='Kafka topic name')
    parser.add_argument('--output', default='metrics.json',
                        help='Output file for metrics')
    parser.add_argument('--verbose', action='store_true',
                        help='Enable debug logging')

    return parser.parse_args()


def main():
    """Main execution"""
    args = parse_args()
    setup_logging(args.verbose)
    logger = logging.getLogger(__name__)

    try:
        # Parse load level
        load_level = LoadLevel.from_string(args.load_level)

        # Run test
        result = run_test(
            test_id=args.test_id,
            domain=args.domain,
            load_level=load_level,
            duration_seconds=args.duration,
            kafka_bootstrap=args.kafka_bootstrap,
            kafka_topic=args.kafka_topic
        )

        # Write metrics to file
        with open(args.output, 'w') as f:
            json.dump(result, f, indent=2)

        logger.info(f"Metrics written to {args.output}")

        # Exit code based on errors
        if result['error_rate_percent'] < 1.0:
            sys.exit(0)
        else:
            logger.warning(f"High error rate: {result['error_rate_percent']:.2f}%")
            sys.exit(1)

    except KeyboardInterrupt:
        logger.warning("Test interrupted by user")
        sys.exit(130)
    except Exception as e:
        logger.error(f"Test failed: {e}", exc_info=True)
        sys.exit(1)


if __name__ == '__main__':
    main()