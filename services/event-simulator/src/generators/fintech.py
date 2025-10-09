"""
FinTech transaction generator - AC2.3 PARTIAL
TODO: Complete the fraud injection methods
"""
import random
from typing import Dict, Any
from .base import BaseGenerator, FraudPattern


class FinTechGenerator(BaseGenerator):
    """Generates wire transfer transactions"""

    CURRENCIES = ['USD', 'EUR', 'GBP', 'JPY', 'CHF']
    COUNTRIES = ['US', 'UK', 'DE', 'FR', 'JP', 'CH', 'CA']
    TRANSFER_TYPES = ['wire', 'ach', 'swift', 'sepa']

    def __init__(self, test_id: str):
        super().__init__(domain='fintech', test_id=test_id)

    def _get_fraud_rates(self) -> Dict[FraudPattern, float]:
        """AC2.3: FinTech fraud distribution"""
        return {
            FraudPattern.STRUCTURING: 0.03,  # 3%
            FraudPattern.MONEY_LAUNDERING: 0.02,  # 2%
            FraudPattern.SYNTHETIC_IDENTITY: 0.01  # 1%
        }

    def _generate_legitimate_transaction(self) -> Dict[str, Any]:
        """Generate clean wire transfer"""

        # Generate account numbers
        account_from = f"ACC{random.randint(1000000, 9999999)}"
        account_to = f"ACC{random.randint(1000000, 9999999)}"

        # Random amount based on transfer type
        transfer_type = random.choice(self.TRANSFER_TYPES)
        amount = self._get_transfer_amount(transfer_type)

        return {
            'account_from': account_from,
            'account_to': account_to,
            'amount': amount,
            'currency': random.choice(self.CURRENCIES),
            'transfer_type': transfer_type,
            'country_from': random.choice(self.COUNTRIES),
            'country_to': random.choice(self.COUNTRIES),
            'purpose': random.choice(['payment', 'invoice', 'salary', 'investment']),
            'ip_address': self._random_ip(),
            'timestamp_initiated': None  # Will be set in base class
        }

    def _inject_fraud_pattern(self, transaction: Dict[str, Any],
                              pattern: FraudPattern) -> Dict[str, Any]:
        """Inject fraud signals"""

        if pattern == FraudPattern.STRUCTURING:
            # TODO: Implement structuring pattern (smurfing)
            # Hint: Multiple transactions just under reporting threshold ($10K)
            # - Amount just under $10,000 (e.g., $9,500 - $9,999)
            # - Add 'related_transactions' count
            # - Same account sending multiple times
            # Reference gaming.py for pattern structure
            pass  # Implement this

        elif pattern == FraudPattern.MONEY_LAUNDERING:
            # TODO: Implement money laundering pattern
            # Hint: Rapid movement of funds through multiple accounts
            # - High amounts
            # - Suspicious country pairs (high-risk jurisdictions)
            # - Add 'layering_depth' field (how many hops)
            # - Purpose marked as 'unclear' or 'business'
            pass  # Implement this

        elif pattern == FraudPattern.SYNTHETIC_IDENTITY:
            # TODO: Implement synthetic identity pattern
            # Hint: New account with suspicious activity
            # - Account age < 30 days
            # - First transaction is high value
            # - SSN/identity mismatch signals
            # - Add 'account_age_days' field
            transaction['account_age_days'] = random.randint(1, 29)
            transaction['identity_verification_score'] = random.uniform(0.1, 0.4)
            # TODO: Add more synthetic identity signals

        return transaction

    @staticmethod
    def _get_transfer_amount(transfer_type: str) -> float:
        """Realistic amounts by transfer type"""
        ranges = {
            'wire': (1000, 100000),
            'ach': (100, 10000),
            'swift': (5000, 500000),
            'sepa': (500, 50000)
        }
        min_amt, max_amt = ranges.get(transfer_type, (100, 10000))
        return round(random.uniform(min_amt, max_amt), 2)