#!/usr/bin/env python3
"""
Quick validation tests for event generator
"""
import sys
import json
import csv
import random
from pathlib import Path
from datetime import datetime, timedelta

# Add src directory to path
sys.path.insert(0, str(Path(__file__).parent.parent / 'src'))

from generators.gaming import GamingGenerator
from generators.ecommerce import EcommerceGenerator
from generators.fintech import FinTechGenerator
from generators.base import TransactionPattern


def sample_generator():
    """
    Generate training data with realistic fraud grouping:
    - Multiple transactions grouped by actor + pattern
    - Pattern-specific time windows
    """
    print("📊 Generating Training Data Files...")

    # Setup paths
    data_dir = Path(__file__).parent.parent / '../../' / 'data' / 'raw'
    data_dir.mkdir(parents=True, exist_ok=True)

    transactions_file = data_dir / 'transactions_samples.jsonl'
    labels_file = data_dir / 'frauds_labels_samples.csv'

    # Configuration
    num_transactions = 200_000
    domains = ['gaming', 'ecommerce', 'fintech']

    print(f"   📝 Generating {num_transactions:,} transactions...")
    print(f"   📂 Output: {data_dir}")

    # Initialize generators
    generators = {
        'gaming': GamingGenerator('train-gaming'),
        'ecommerce': EcommerceGenerator('train-ecom'),
        'fintech': FinTechGenerator('train-fintech')
    }

    # Pattern rates per domain
    domain_rates = {
        'gaming': {
            TransactionPattern.FRAUD_ACCOUNT_TAKEOVER: 0.05,
            TransactionPattern.FRAUD_GOLD_FARMING: 0.03,
            TransactionPattern.FRAUD_CHARGEBACK_FRAUD: 0.02,
            TransactionPattern.REGULAR_CASUAL_PLAYER: 0.60,
            TransactionPattern.REGULAR_WHALE_SPENDER: 0.15,
            TransactionPattern.REGULAR_GRINDER: 0.15,
        },
        'ecommerce': {
            TransactionPattern.FRAUD_CARD_TESTING: 0.03,
            TransactionPattern.FRAUD_FRIENDLY_FRAUD: 0.03,
            TransactionPattern.FRAUD_PROMO_ABUSE: 0.02,
            TransactionPattern.REGULAR_SHOPPER: 0.75,
            TransactionPattern.REGULAR_WINDOW_SHOPPER: 0.17,
        },
        'fintech': {
            TransactionPattern.FRAUD_STRUCTURING: 0.02,
            TransactionPattern.FRAUD_MONEY_LAUNDERING: 0.03,
            TransactionPattern.FRAUD_SYNTHETIC_IDENTITY: 0.02,
            TransactionPattern.REGULAR_SAVER: 0.55,
            TransactionPattern.REGULAR_BILL_PAYER: 0.38,
        }
    }

    # ✅ Pattern-specific time windows for grouping
    time_windows = {
        'fraud_account_takeover': timedelta(hours=4),
        'fraud_gold_farming': timedelta(days=7),
        'fraud_chargeback_fraud': timedelta(hours=24),
        'fraud_card_testing': timedelta(hours=12),
        'fraud_friendly_fraud': timedelta(days=30),
        'fraud_promo_abuse': timedelta(days=7),
        'fraud_structuring': timedelta(days=30),
        'fraud_money_laundering': timedelta(days=14),
        'fraud_synthetic_identity': timedelta(days=7),
    }

    # Collect all transactions first
    all_transactions = []
    transaction_count = 0
    start_time = datetime.now() - timedelta(days=90)

    print("   🔄 Phase 1: Generating transactions...")

    batch_size = 5000
    num_batches = num_transactions // batch_size

    for batch_idx in range(num_batches):
        domain = domains[batch_idx % len(domains)]
        generator = generators[domain]
        fraud_rates = domain_rates[domain]

        events = generator.generate_batch(batch_size, fraud_rates)

        for event in events:
            time_offset = timedelta(
                days=random.randint(0, 89),
                hours=random.randint(0, 23),
                minutes=random.randint(0, 59),
                seconds=random.randint(0, 59)
            )
            timestamp = start_time + time_offset

            transaction = {
                'transaction_id': f'txn_{transaction_count:06d}',
                'customer_id': event.get('actor_id', f'cust_{random.randint(1, 10000):05d}'),
                'domain': domain,
                'amount': float(event.get('amount', random.uniform(10, 1000))),
                'currency': event.get('currency', 'USD'),
                'timestamp': timestamp,
                'timestamp_iso': timestamp.isoformat(),
                'ip_address': event.get('ip_address', f'192.168.{random.randint(1, 255)}.{random.randint(1, 255)}'),
                'pattern': str(event.get('pattern', 'UNKNOWN')),
                'is_fraud': event.get('is_fraud', False),
                'player_id': event.get('player_id'),
                'game_id': event.get('game_id'),
                'item_type': event.get('item_type'),
                'item_name': event.get('item_name'),
                'payment_method': event.get('payment_method'),
                'device_id': event.get('device_id'),
                'session_length_sec': event.get('session_length_sec'),
                'account_from': event.get('account_from'),
                'account_to': event.get('account_to'),
                'transfer_type': event.get('transfer_type'),
                'country_from': event.get('country_from'),
                'country_to': event.get('country_to'),
                'purpose': event.get('purpose'),
                'user_id': event.get('user_id'),
                'cart_items': json.dumps(event.get('cart_items', [])),
                'shipping_address': event.get('shipping_address'),
                'billing_address': event.get('billing_address'),
                'device_fingerprint': event.get('device_fingerprint'),
                'session_duration_sec': event.get('session_duration_sec'),
                'metadata': event.get('metadata', {})
            }

            all_transactions.append(transaction)
            transaction_count += 1

        progress = (batch_idx + 1) / num_batches * 100
        print(f"      ⏳ {progress:.0f}% ({transaction_count:,} transactions)")

    # ✅ Phase 2: Group fraud transactions into alerts
    print("   🔄 Phase 2: Grouping fraud transactions into alerts...")

    # fraud_transactions = [t for t in all_transactions if t['is_fraud']] // commented as it works better for ML training to include ALSO regulars
    fraud_transactions = all_transactions

    # Sort by actor_id, pattern, timestamp
    fraud_transactions.sort(key=lambda x: (x['customer_id'], x['pattern'], x['timestamp']))

    fraud_alerts = []
    alert_id_counter = 1

    i = 0
    while i < len(fraud_transactions):
        current = fraud_transactions[i]
        actor_id = current['customer_id']
        pattern = current['pattern']
        start_time = current['timestamp']

        # ✅ Get pattern-specific time window
        time_window = time_windows.get(pattern, timedelta(hours=1))

        # Collect all transactions from same actor+pattern within time window
        grouped_txns = [current]
        j = i + 1

        while j < len(fraud_transactions):
            next_txn = fraud_transactions[j]

            # Same actor + pattern + within time window?
            if (next_txn['customer_id'] == actor_id and
                    next_txn['pattern'] == pattern and
                    next_txn['timestamp'] - start_time <= time_window):
                grouped_txns.append(next_txn)
                j += 1
            else:
                break

        # Create single alert for grouped transactions
        total_amount = sum(t['amount'] for t in grouped_txns)
        txn_ids = [t['transaction_id'] for t in grouped_txns]

        fraud_alerts.append({
            'alert_id': alert_id_counter,
            'transaction_id': grouped_txns[0]['transaction_id'],
            'domain': current['domain'],
            'actor_id': actor_id,
            'amount': round(total_amount, 2),
            'transaction_count': len(grouped_txns),
            'timestamp': grouped_txns[0]['timestamp_iso'],
            'ip_address': current['ip_address'],
            'pattern': pattern,
            'fraud_label': True,
            'fraud_type': pattern,
            'confidence': round(random.uniform(0.7, 0.99), 2),
            'label_source': random.choice(['rule_based', 'manual_review', 'chargeback']),
            'label_timestamp': grouped_txns[-1]['timestamp_iso'],
            'related_transaction_ids': json.dumps(txn_ids)
        })

        alert_id_counter += 1
        i = j

    fraud_count = len(fraud_transactions)
    alert_count = len(fraud_alerts)

    print(f"      ✅ {fraud_count:,} fraud transactions → {alert_count:,} alerts")
    if alert_count > 0:
        print(f"      📊 Avg {fraud_count / alert_count:.1f} transactions per alert")

    # Phase 3: Write files
    print("   🔄 Phase 3: Writing output files...")

    # Write transactions JSONL
    with open(transactions_file, 'w') as txn_file:
        for txn in all_transactions:
            output_txn = {k: v for k, v in txn.items() if k not in ['timestamp']}
            output_txn['timestamp'] = txn['timestamp_iso']
            txn_file.write(json.dumps(output_txn) + '\n')

    # Write fraud labels CSV
    with open(labels_file, 'w', newline='') as csv_file:
        fieldnames = [
            'alert_id',
            'transaction_id',
            'domain',
            'actor_id',
            'amount',
            'transaction_count',
            'timestamp',
            'ip_address',
            'pattern',
            'fraud_label',
            'fraud_type',
            'confidence',
            'label_source',
            'label_timestamp',
            'related_transaction_ids'
        ]
        writer = csv.DictWriter(csv_file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(fraud_alerts)

    # Stats
    txn_size_mb = transactions_file.stat().st_size / (1024 * 1024)
    labels_size_kb = labels_file.stat().st_size / 1024
    fraud_rate = (fraud_count / transaction_count * 100)

    # Pattern breakdown
    pattern_counts = {}
    for alert in fraud_alerts:
        pattern = alert['pattern']
        pattern_counts[pattern] = pattern_counts.get(pattern, 0) + 1

    # Transaction grouping stats
    if alert_count > 0:
        txn_per_alert = [alert['transaction_count'] for alert in fraud_alerts]
        min_txns = min(txn_per_alert)
        max_txns = max(txn_per_alert)
        avg_txns = sum(txn_per_alert) / len(txn_per_alert)
    else:
        min_txns = max_txns = avg_txns = 0

    print(f"\n   ✅ COMPLETE!")
    print(f"   📄 Transactions: {transactions_file.name}")
    print(f"      - Records: {transaction_count:,}")
    print(f"      - Size: {txn_size_mb:.1f} MB")
    print(f"      - Domains: {', '.join(domains)}")
    print(f"   📄 Fraud Labels: {labels_file.name}")
    print(f"      - Fraud transactions: {fraud_count:,} ({fraud_rate:.2f}%)")
    print(f"      - Fraud alerts: {alert_count:,}")
    if alert_count > 0:
        print(f"      - Transactions per alert: {min_txns}-{max_txns} (avg: {avg_txns:.1f})")
    print(f"      - Size: {labels_size_kb:.1f} KB")
    print(f"      - Pattern breakdown:")
    for pattern, count in sorted(pattern_counts.items(), key=lambda x: -x[1]):
        if alert_count > 0:
            pct = count / alert_count * 100
            print(f"        • {pattern}: {count:,} alerts ({pct:.1f}%)")
    print(f"   📂 Location: {data_dir}")
    print("   PASS\n")

    return True


def main():
    print("=" * 60)
    print("PatternAlarm - Training Data Generator")
    print("=" * 60)

    success = sample_generator()

    print("=" * 60)
    if success:
        print("✅ Training data generation complete!")
        print("\n📍 Next steps:")
        print("   1. Check data/raw/ for generated files")
        print("   2. Run Spark ETL notebook to process data")
        print("   3. Train fraud detection model")
    else:
        print("❌ Training data generation failed")
    print("=" * 60)


if __name__ == '__main__':
    main()