"""
Base transaction generator with fraud pattern injection
Production risk: Fraud patterns are deterministic - real fraudsters adapt faster
"""
import random
import string
import time
from abc import ABC, abstractmethod
from dataclasses import dataclass, asdict
from datetime import datetime, timezone
from typing import List, Dict, Any
from enum import Enum


class FraudPattern(Enum):
    NONE = "none"
    # Gaming patterns
    ACCOUNT_TAKEOVER = "account_takeover"
    GOLD_FARMING = "gold_farming"
    CHARGEBACK_FRAUD = "chargeback_fraud"
    # E-commerce patterns
    CARD_TESTING = "card_testing"
    FRIENDLY_FRAUD = "friendly_fraud"
    PROMO_ABUSE = "promo_abuse"
    # FinTech patterns
    STRUCTURING = "structuring"
    MONEY_LAUNDERING = "money_laundering"
    SYNTHETIC_IDENTITY = "synthetic_identity"


@dataclass
class BaseTransaction:
    """Base transaction fields - all domains inherit this"""
    transaction_id: str
    domain: str
    test_id: str
    timestamp: str
    fraud_pattern: str
    is_fraud: bool
    metadata: Dict[str, Any]


class BaseGenerator(ABC):
    """
    Base generator with fraud injection logic
    Subclasses implement domain-specific transaction generation
    """

    def __init__(self, domain: str, test_id: str):
        self.domain = domain
        self.test_id = test_id
        self.fraud_rates = self._get_fraud_rates()

    @abstractmethod
    def _get_fraud_rates(self) -> Dict[FraudPattern, float]:
        """Return fraud pattern rates for this domain (e.g., 0.05 = 5%)"""
        pass

    @abstractmethod
    def _generate_legitimate_transaction(self) -> Dict[str, Any]:
        """Generate a clean transaction for this domain"""
        pass

    @abstractmethod
    def _inject_fraud_pattern(self, transaction: Dict[str, Any],
                              pattern: FraudPattern) -> Dict[str, Any]:
        """Modify transaction to exhibit fraud pattern"""
        pass

    def generate_batch(self, count: int) -> List[Dict[str, Any]]:
        """
        Generate batch of transactions with fraud patterns injected
        Returns: List of dicts ready for Kafka serialization
        """
        transactions = []

        for i in range(count):
            # Determine if this transaction should be fraudulent
            fraud_pattern, is_fraud = self._select_fraud_pattern()

            # Generate base transaction
            transaction = self._generate_legitimate_transaction()

            # Inject fraud pattern if needed
            if is_fraud:
                transaction = self._inject_fraud_pattern(transaction, fraud_pattern)

            # Add metadata
            transaction.update({
                'transaction_id': self._generate_id(),
                'domain': self.domain,
                'test_id': self.test_id,
                'timestamp': datetime.now(timezone.utc).isoformat(),
                'fraud_pattern': fraud_pattern.value,
                'is_fraud': is_fraud,
                'batch_position': i
            })

            transactions.append(transaction)

        return transactions

    def _select_fraud_pattern(self) -> tuple[FraudPattern, bool]:
        """
        Probabilistically select fraud pattern based on configured rates
        Returns: (pattern, is_fraud_flag)
        """
        roll = random.random()
        cumulative = 0.0

        for pattern, rate in self.fraud_rates.items():
            cumulative += rate
            if roll < cumulative:
                return pattern, True

        return FraudPattern.NONE, False

    @staticmethod
    def _generate_id(length: int = 16) -> str:
        """Generate random transaction ID"""
        return ''.join(random.choices(string.ascii_uppercase + string.digits, k=length))

    @staticmethod
    def _random_ip() -> str:
        """Generate random IP address"""
        return f"{random.randint(1, 255)}.{random.randint(0, 255)}.{random.randint(0, 255)}.{random.randint(1, 255)}"

    @staticmethod
    def _random_user_agent() -> str:
        """Generate realistic user agent"""
        browsers = ['Chrome/118.0', 'Firefox/119.0', 'Safari/17.0']
        systems = ['Windows NT 10.0', 'Macintosh; Intel Mac OS X 10_15_7', 'X11; Linux x86_64']
        return f"Mozilla/5.0 ({random.choice(systems)}) {random.choice(browsers)}"