"""
Gaming transaction generator - AC2.1 COMPLETE
Use this as reference for implementing ecommerce.py and fintech.py
"""
import random
from typing import Dict, Any
from .base import BaseGenerator, FraudPattern


class GamingGenerator(BaseGenerator):
    """Generates in-game purchase transactions"""

    GAMES = ['FortniteClone', 'WoWRipoff', 'LOL2Electric', 'ApexLegends', 'Valorant']
    ITEM_TYPES = ['skin', 'weapon', 'lootbox', 'currency', 'battle_pass', 'emote']
    CURRENCIES = ['USD', 'EUR', 'GBP']

    def __init__(self, test_id: str):
        super().__init__(domain='gaming', test_id=test_id)

    def _get_fraud_rates(self) -> Dict[FraudPattern, float]:
        """AC2.1: Gaming fraud distribution"""
        return {
            FraudPattern.ACCOUNT_TAKEOVER: 0.05,  # 5%
            FraudPattern.GOLD_FARMING: 0.03,  # 3%
            FraudPattern.CHARGEBACK_FRAUD: 0.02  # 2%
        }

    def _generate_legitimate_transaction(self) -> Dict[str, Any]:
        """Generate clean gaming transaction"""
        player_id = f"P{random.randint(100000, 999999)}"
        game = random.choice(self.GAMES)
        item_type = random.choice(self.ITEM_TYPES)

        return {
            'player_id': player_id,
            'game_id': game,
            'item_type': item_type,
            'item_name': f"{item_type.capitalize()}_{random.randint(1, 999)}",
            'amount': self._get_item_price(item_type),
            'currency': random.choice(self.CURRENCIES),
            'payment_method': random.choice(['credit_card', 'paypal', 'steam_wallet']),
            'ip_address': self._random_ip(),
            'device_id': self._generate_id(12),
            'session_length': random.randint(300, 7200)
        }

    def _inject_fraud_pattern(self, transaction: Dict[str, Any],
                              pattern: FraudPattern) -> Dict[str, Any]:
        """Inject fraud signals into transaction"""

        if pattern == FraudPattern.ACCOUNT_TAKEOVER:
            # Sudden location change + high-value purchase + new device
            transaction['ip_address'] = self._suspicious_ip()
            transaction['device_id'] = self._generate_id(12)  # New device
            transaction['amount'] = random.uniform(99, 499)
            transaction['purchase_velocity'] = random.randint(5, 15)
            transaction['geo_mismatch'] = True

        elif pattern == FraudPattern.GOLD_FARMING:
            # Repetitive low-value currency purchases + bot-like timing
            transaction['item_type'] = 'currency'
            transaction['amount'] = random.uniform(0.99, 4.99)
            transaction['session_length'] = random.randint(10, 60)
            transaction['purchase_pattern'] = 'repetitive'
            transaction['time_between_purchases'] = random.randint(1, 5)

        elif pattern == FraudPattern.CHARGEBACK_FRAUD:
            # Large purchase + stolen card indicators
            transaction['amount'] = random.uniform(199, 999)
            transaction['card_bin'] = self._stolen_card_bin()
            transaction['cvv_match'] = False
            transaction['billing_address_mismatch'] = True

        return transaction

    @staticmethod
    def _get_item_price(item_type: str) -> float:
        """Realistic pricing by item type"""
        prices = {
            'skin': (4.99, 19.99),
            'weapon': (9.99, 29.99),
            'lootbox': (0.99, 4.99),
            'currency': (4.99, 99.99),
            'battle_pass': (9.99, 24.99),
            'emote': (1.99, 9.99)
        }
        min_price, max_price = prices.get(item_type, (1.99, 19.99))
        return round(random.uniform(min_price, max_price), 2)

    @staticmethod
    def _suspicious_ip() -> str:
        """Return IP from known VPN/proxy ranges"""
        vpn_ranges = [(45, 142), (185, 220)]
        first, second = random.choice(vpn_ranges)
        return f"{first}.{second}.{random.randint(0, 255)}.{random.randint(1, 255)}"

    @staticmethod
    def _stolen_card_bin() -> str:
        """Return card BIN from known compromised batches"""
        return random.choice(['424242', '411111', '555555'])