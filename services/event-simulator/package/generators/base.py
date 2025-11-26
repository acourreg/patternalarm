"""
Base transaction generator with multi-transaction fraud pattern injection
Fraud emerges across multiple transactions, not single events
"""
import random
import string
from abc import ABC, abstractmethod
from dataclasses import dataclass
from datetime import datetime, timezone, timedelta
from typing import List, Dict, Any, Tuple
from enum import Enum


class TransactionPattern(Enum):
    """Transaction patterns - fraud requires multiple transactions to detect"""
    # Gaming patterns
    FRAUD_ACCOUNT_TAKEOVER = ("fraud_account_takeover", "gaming")
    FRAUD_GOLD_FARMING = ("fraud_gold_farming", "gaming")
    FRAUD_CHARGEBACK_FRAUD = ("fraud_chargeback_fraud", "gaming")
    REGULAR_CASUAL_PLAYER = ("regular_casual_player", "gaming")
    REGULAR_WHALE_SPENDER = ("regular_whale_spender", "gaming")
    REGULAR_GRINDER = ("regular_grinder", "gaming")

    # E-commerce patterns
    FRAUD_CARD_TESTING = ("fraud_card_testing", "ecommerce")
    FRAUD_FRIENDLY_FRAUD = ("fraud_friendly_fraud", "ecommerce")
    FRAUD_PROMO_ABUSE = ("fraud_promo_abuse", "ecommerce")
    REGULAR_SHOPPER = ("regular_shopper", "ecommerce")
    REGULAR_WINDOW_SHOPPER = ("regular_window_shopper", "ecommerce")

    # FinTech patterns
    FRAUD_STRUCTURING = ("fraud_structuring", "fintech")
    FRAUD_MONEY_LAUNDERING = ("fraud_money_laundering", "fintech")
    FRAUD_SYNTHETIC_IDENTITY = ("fraud_synthetic_identity", "fintech")
    REGULAR_SAVER = ("regular_saver", "fintech")
    REGULAR_BILL_PAYER = ("regular_bill_payer", "fintech")

    def __init__(self, pattern_name: str, domain: str):
        self.pattern_name = pattern_name
        self.domain = domain

    def is_fraud(self) -> bool:
        """Check if pattern represents fraud"""
        return self.pattern_name.startswith("fraud_")

    @classmethod
    def for_domain(cls, domain: str) -> List['TransactionPattern']:
        """Get all patterns for a specific domain"""
        return [pattern for pattern in cls if pattern.domain == domain]


@dataclass
class TransactionSequence:
    """A sequence of related transactions from a single pattern"""
    pattern: TransactionPattern
    actor_id: str  # player_id, user_id, account_id
    transactions: List[Dict[str, Any]]
    start_time: datetime


class BaseGenerator(ABC):
    """
    Base generator creating realistic transaction sequences
    Fraud patterns span multiple transactions mixed with legitimate activity
    """

    def __init__(self, test_id: str):
        self.test_id = test_id
        self.base_timestamp = datetime.now(timezone.utc)

    @abstractmethod
    def _generate_pattern_sequence(self, pattern: TransactionPattern,
                                   actor_id: str) -> List[Dict[str, Any]]:
        """
        Generate 2-10 transactions exhibiting the pattern
        Subclasses determine realistic sequence length per pattern

        Returns: List of transaction dicts (without metadata, added later)
        """
        pass

    def generate_batch(self, count: int, pattern_rates: Dict[TransactionPattern, float]) -> List[Dict[str, Any]]:
        """
        Generate batch of transactions with realistic pattern mixing

        Args:
            count: Number of transactions to generate
            pattern_rates: Distribution of patterns, e.g.
                {TransactionPattern.FRAUD_GOLD_FARMING: 0.03,
                 TransactionPattern.REGULAR_CASUAL_PLAYER: 0.70, ...}
                Rates should sum to ~1.0

        Strategy:
        1. Create sequences (each 2-10 transactions) until we reach target count
        2. Mix sequences together chronologically
        3. Add metadata to individual transactions

        Returns: List of dicts ready for Kafka serialization
        """
        # Infer domain from first pattern
        domain = next(iter(pattern_rates.keys())).domain

        sequences = []
        transactions_generated = 0

        # Generate sequences until we hit target count
        while transactions_generated < count:
            # Select pattern based on provided rates
            pattern = self._select_pattern(pattern_rates)

            # Determine sequence length (2-10, but don't exceed remaining count)
            remaining = count - transactions_generated
            sequence_length = min(random.randint(2, 10), remaining)

            # Generate actor ID for this sequence
            actor_id = self._generate_actor_id()

            # Generate the transaction sequence
            txn_list = self._generate_pattern_sequence(pattern, actor_id)

            # Trim to actual sequence length needed
            txn_list = txn_list[:sequence_length]

            # Create sequence metadata
            sequence = TransactionSequence(
                pattern=pattern,
                actor_id=actor_id,
                transactions=txn_list,
                start_time=self.base_timestamp + timedelta(seconds=transactions_generated * 0.1)
            )

            sequences.append(sequence)
            transactions_generated += len(txn_list)

        # Mix sequences chronologically and add metadata
        return self._mix_and_finalize(sequences, domain)

    def _select_pattern(self, pattern_rates: Dict[TransactionPattern, float]) -> TransactionPattern:
        """Probabilistically select pattern based on provided rates"""
        roll = random.random()
        cumulative = 0.0

        for pattern, rate in pattern_rates.items():
            cumulative += rate
            if roll < cumulative:
                return pattern

        # Fallback to first regular pattern if rates don't sum to 1.0
        return next(p for p in pattern_rates.keys() if not p.is_fraud())

    def _mix_and_finalize(self, sequences: List[TransactionSequence], domain: str) -> List[Dict[str, Any]]:
        """
        Mix sequences chronologically and add metadata

        Maintains temporal ordering while interleaving different actors
        """
        all_transactions = []

        for seq in sequences:
            for i, txn in enumerate(seq.transactions):
                # Calculate timestamp for this transaction in sequence
                txn_timestamp = seq.start_time + timedelta(seconds=i * random.uniform(1, 30))

                # Add standard metadata
                txn.update({
                    'transaction_id': self._generate_id(),
                    'domain': domain,
                    'test_id': self.test_id,
                    'timestamp': txn_timestamp.isoformat(),
                    'pattern': seq.pattern.pattern_name,
                    'is_fraud': seq.pattern.is_fraud(),
                    'actor_id': seq.actor_id,
                    'sequence_position': i
                })

                all_transactions.append((txn_timestamp, txn))

        # Sort by timestamp to maintain chronological order
        all_transactions.sort(key=lambda x: x[0])

        # Return just the transactions (drop timestamp used for sorting)
        return [txn for _, txn in all_transactions]

    def _generate_actor_id(self) -> str:
        """Generate unique actor ID (player, user, account)"""
        return f"A{random.randint(100000, 999999)}"

    @staticmethod
    def _generate_id(length: int = 16) -> str:
        """Generate random transaction ID"""
        return ''.join(random.choices(string.ascii_uppercase + string.digits, k=length))

    @staticmethod
    def _random_ip() -> str:
        """Generate random IP address"""
        return f"{random.randint(1, 255)}.{random.randint(0, 255)}.{random.randint(0, 255)}.{random.randint(1, 255)}"

    @staticmethod
    def _suspicious_ip() -> str:
        """Return IP from known VPN/proxy ranges"""
        vpn_ranges = [(45, 142), (185, 220)]
        first, second = random.choice(vpn_ranges)
        return f"{first}.{second}.{random.randint(0, 255)}.{random.randint(1, 255)}"

    @staticmethod
    def _random_user_agent() -> str:
        """Generate realistic user agent"""
        browsers = ['Chrome/118.0', 'Firefox/119.0', 'Safari/17.0']
        systems = ['Windows NT 10.0', 'Macintosh; Intel Mac OS X 10_15_7', 'X11; Linux x86_64']
        return f"Mozilla/5.0 ({random.choice(systems)}) {random.choice(browsers)}"