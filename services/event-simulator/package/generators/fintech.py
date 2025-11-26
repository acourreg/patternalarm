"""
FinTech transaction generator - Simple and realistic
"""
import random
from typing import Dict, Any, List
from .base import BaseGenerator, TransactionPattern


class FinTechGenerator(BaseGenerator):
    """Generates wire transfer transactions"""

    CURRENCIES = ['USD', 'EUR', 'GBP', 'JPY', 'CHF']
    COUNTRIES = ['US', 'UK', 'DE', 'FR', 'JP', 'CH', 'CA']
    HIGH_RISK_COUNTRIES = ['KY', 'PA', 'BZ', 'VG']  # Cayman Islands, Panama, Belize, BVI
    TRANSFER_TYPES = ['wire', 'ach', 'swift', 'sepa']
    PURPOSES = ['payment', 'invoice', 'salary', 'investment', 'personal', 'business']

    def __init__(self, test_id: str):
        super().__init__(test_id=test_id)

    def _generate_pattern_sequence(self, pattern: TransactionPattern, actor_id: str) -> List[Dict[str, Any]]:
        """Generate 2-10 transactions for the given pattern"""

        if pattern == TransactionPattern.FRAUD_STRUCTURING:
            return self._structuring_sequence(actor_id)

        elif pattern == TransactionPattern.FRAUD_MONEY_LAUNDERING:
            return self._money_laundering_sequence(actor_id)

        elif pattern == TransactionPattern.FRAUD_SYNTHETIC_IDENTITY:
            return self._synthetic_identity_sequence(actor_id)

        elif pattern == TransactionPattern.REGULAR_SAVER:
            return self._regular_saver_sequence(actor_id)

        elif pattern == TransactionPattern.REGULAR_BILL_PAYER:
            return self._bill_payer_sequence(actor_id)

        else:
            return self._regular_saver_sequence(actor_id)

    # ========================================================================
    # Fraud Patterns
    # ========================================================================

    def _structuring_sequence(self, account_from: str) -> List[Dict[str, Any]]:
        """Structuring: 5-10 transfers just under $10K reporting threshold"""
        num_txns = random.randint(5, 10)
        currency = 'USD'
        ip = self._random_ip()
        country_from = 'US'
        transfer_type = 'wire'

        # Multiple destination accounts (smurfing)
        transactions = []
        for _ in range(num_txns):
            account_to = f"ACC{random.randint(1000000, 9999999)}"

            transactions.append({
                'account_from': account_from,
                'account_to': account_to,
                'amount': round(random.uniform(9500, 9999), 2),  # Just under $10K
                'currency': currency,
                'transfer_type': transfer_type,
                'country_from': country_from,
                'country_to': random.choice(self.COUNTRIES),
                'purpose': 'payment',
                'ip_address': ip
            })

        return transactions

    def _money_laundering_sequence(self, account_from: str) -> List[Dict[str, Any]]:
        """Money laundering: 4-8 rapid transfers through layered accounts"""
        num_txns = random.randint(4, 8)
        currency = random.choice(self.CURRENCIES)
        ip = self._random_ip()
        transfer_type = 'swift'

        # Layering - money moves through multiple accounts
        accounts = [account_from]
        for _ in range(num_txns):
            accounts.append(f"ACC{random.randint(1000000, 9999999)}")

        transactions = []
        for i in range(num_txns):
            # High-risk jurisdictions involved
            country_to = random.choice(self.HIGH_RISK_COUNTRIES + self.COUNTRIES)

            transactions.append({
                'account_from': accounts[i],
                'account_to': accounts[i + 1],
                'amount': round(random.uniform(50000, 500000), 2),
                'currency': currency,
                'transfer_type': transfer_type,
                'country_from': random.choice(self.COUNTRIES),
                'country_to': country_to,
                'purpose': random.choice(['business', 'investment']),
                'ip_address': ip
            })

        return transactions

    def _synthetic_identity_sequence(self, account_from: str) -> List[Dict[str, Any]]:
        """Synthetic identity: 3-5 transactions from newly created fake account"""
        num_txns = random.randint(3, 5)
        currency = 'USD'
        ip = self._random_ip()
        transfer_type = random.choice(['wire', 'ach'])
        country = 'US'

        transactions = []
        for _ in range(num_txns):
            account_to = f"ACC{random.randint(1000000, 9999999)}"

            transactions.append({
                'account_from': account_from,
                'account_to': account_to,
                'amount': round(random.uniform(5000, 50000), 2),
                'currency': currency,
                'transfer_type': transfer_type,
                'country_from': country,
                'country_to': country,
                'purpose': random.choice(self.PURPOSES),
                'ip_address': ip
            })

        return transactions

    # ========================================================================
    # Regular Patterns
    # ========================================================================

    def _regular_saver_sequence(self, account_from: str) -> List[Dict[str, Any]]:
        """Regular saver: 2-4 recurring savings transfers"""
        num_txns = random.randint(2, 4)
        currency = random.choice(self.CURRENCIES)
        ip = self._random_ip()
        transfer_type = 'ach'
        country = random.choice(self.COUNTRIES)

        # Same destination (savings account)
        account_to = f"ACC{random.randint(1000000, 9999999)}"

        transactions = []
        for _ in range(num_txns):
            transactions.append({
                'account_from': account_from,
                'account_to': account_to,
                'amount': round(random.uniform(200, 2000), 2),
                'currency': currency,
                'transfer_type': transfer_type,
                'country_from': country,
                'country_to': country,
                'purpose': 'personal',
                'ip_address': ip
            })

        return transactions

    def _bill_payer_sequence(self, account_from: str) -> List[Dict[str, Any]]:
        """Bill payer: 3-6 recurring bill payments"""
        num_txns = random.randint(3, 6)
        currency = random.choice(self.CURRENCIES)
        ip = self._random_ip()
        transfer_type = 'ach'
        country = random.choice(self.COUNTRIES)

        transactions = []
        for _ in range(num_txns):
            # Different payees (utilities, rent, etc)
            account_to = f"ACC{random.randint(1000000, 9999999)}"

            transactions.append({
                'account_from': account_from,
                'account_to': account_to,
                'amount': round(random.uniform(50, 1500), 2),
                'currency': currency,
                'transfer_type': transfer_type,
                'country_from': country,
                'country_to': country,
                'purpose': random.choice(['payment', 'invoice']),
                'ip_address': ip
            })

        return transactions

    # ========================================================================
    # Helper Methods
    # ========================================================================

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