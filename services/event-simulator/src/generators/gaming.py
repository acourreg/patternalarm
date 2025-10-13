"""
Gaming transaction generator - Simple and realistic
"""
import random
from typing import Dict, Any, List
from .base import BaseGenerator, TransactionPattern


class GamingGenerator(BaseGenerator):
    """Generates in-game purchase transactions"""

    GAMES = ['FortniteClone', 'WoWRipoff', 'LOL2Electric', 'ApexLegends', 'Valorant']
    ITEM_TYPES = ['skin', 'weapon', 'lootbox', 'currency', 'battle_pass', 'emote']
    CURRENCIES = ['USD', 'EUR', 'GBP']
    PAYMENT_METHODS = ['credit_card', 'paypal', 'steam_wallet']

    def __init__(self, test_id: str):
        super().__init__(test_id=test_id)

    def _generate_pattern_sequence(self, pattern: TransactionPattern, actor_id: str) -> List[Dict[str, Any]]:
        """Generate 2-10 transactions for the given pattern"""

        if pattern == TransactionPattern.FRAUD_ACCOUNT_TAKEOVER:
            return self._account_takeover_sequence(actor_id)

        elif pattern == TransactionPattern.FRAUD_GOLD_FARMING:
            return self._gold_farming_sequence(actor_id)

        elif pattern == TransactionPattern.FRAUD_CHARGEBACK_FRAUD:
            return self._chargeback_fraud_sequence(actor_id)

        elif pattern == TransactionPattern.REGULAR_CASUAL_PLAYER:
            return self._casual_player_sequence(actor_id)

        elif pattern == TransactionPattern.REGULAR_WHALE_SPENDER:
            return self._whale_spender_sequence(actor_id)

        elif pattern == TransactionPattern.REGULAR_GRINDER:
            return self._grinder_sequence(actor_id)

        else:
            return self._casual_player_sequence(actor_id)

    # ========================================================================
    # Fraud Patterns
    # ========================================================================

    def _account_takeover_sequence(self, player_id: str) -> List[Dict[str, Any]]:
        """Account takeover: 3-5 high-value purchases from new location/device"""
        num_txns = random.randint(3, 5)
        game = random.choice(self.GAMES)
        currency = random.choice(self.CURRENCIES)

        # Attacker's characteristics
        suspicious_ip = self._suspicious_ip()
        new_device = self._generate_id(12)
        payment = random.choice(self.PAYMENT_METHODS)

        transactions = []
        for _ in range(num_txns):
            item_type = random.choice(['skin', 'weapon', 'battle_pass'])
            transactions.append({
                'player_id': player_id,
                'game_id': game,
                'item_type': item_type,
                'item_name': f"Premium_{item_type}_{random.randint(1, 999)}",
                'amount': round(random.uniform(50, 200), 2),
                'currency': currency,
                'payment_method': payment,
                'ip_address': suspicious_ip,
                'device_id': new_device,
                'session_length_sec': random.randint(30, 120)
            })

        return transactions

    def _gold_farming_sequence(self, player_id: str) -> List[Dict[str, Any]]:
        """Gold farming: 5-10 rapid small currency purchases"""
        num_txns = random.randint(5, 10)
        game = random.choice(self.GAMES)
        currency = 'USD'
        ip = self._random_ip()
        device = self._generate_id(12)

        transactions = []
        for _ in range(num_txns):
            transactions.append({
                'player_id': player_id,
                'game_id': game,
                'item_type': 'currency',
                'item_name': f"Gold_{random.randint(100, 500)}",
                'amount': round(random.uniform(0.99, 4.99), 2),
                'currency': currency,
                'payment_method': 'credit_card',
                'ip_address': ip,
                'device_id': device,
                'session_length_sec': random.randint(5, 30)
            })

        return transactions

    def _chargeback_fraud_sequence(self, player_id: str) -> List[Dict[str, Any]]:
        """Chargeback fraud: 2-4 expensive purchases that will be disputed"""
        num_txns = random.randint(2, 4)
        game = random.choice(self.GAMES)
        currency = random.choice(self.CURRENCIES)
        ip = self._random_ip()
        device = self._generate_id(12)

        transactions = []
        for _ in range(num_txns):
            item_type = random.choice(['lootbox', 'battle_pass', 'currency'])
            transactions.append({
                'player_id': player_id,
                'game_id': game,
                'item_type': item_type,
                'item_name': f"Bundle_{item_type}_{random.randint(1, 100)}",
                'amount': round(random.uniform(100, 500), 2),
                'currency': currency,
                'payment_method': 'credit_card',
                'ip_address': ip,
                'device_id': device,
                'session_length_sec': random.randint(60, 300)
            })

        return transactions

    # ========================================================================
    # Regular Patterns
    # ========================================================================

    def _casual_player_sequence(self, player_id: str) -> List[Dict[str, Any]]:
        """Casual player: 2-3 varied purchases"""
        num_txns = random.randint(2, 3)
        game = random.choice(self.GAMES)
        currency = random.choice(self.CURRENCIES)
        ip = self._random_ip()
        device = self._generate_id(12)
        payment = random.choice(self.PAYMENT_METHODS)

        transactions = []
        for _ in range(num_txns):
            item_type = random.choice(self.ITEM_TYPES)
            transactions.append({
                'player_id': player_id,
                'game_id': game,
                'item_type': item_type,
                'item_name': f"{item_type.capitalize()}_{random.randint(1, 999)}",
                'amount': self._get_item_price(item_type),
                'currency': currency,
                'payment_method': payment,
                'ip_address': ip,
                'device_id': device,
                'session_length_sec': random.randint(600, 3600)
            })

        return transactions

    def _whale_spender_sequence(self, player_id: str) -> List[Dict[str, Any]]:
        """Whale spender: 4-8 high-value purchases"""
        num_txns = random.randint(4, 8)
        game = random.choice(self.GAMES)
        currency = random.choice(self.CURRENCIES)
        ip = self._random_ip()
        device = self._generate_id(12)
        payment = random.choice(self.PAYMENT_METHODS)

        transactions = []
        for _ in range(num_txns):
            item_type = random.choice(['lootbox', 'currency', 'battle_pass'])
            transactions.append({
                'player_id': player_id,
                'game_id': game,
                'item_type': item_type,
                'item_name': f"Premium_{item_type}_{random.randint(1, 100)}",
                'amount': round(random.uniform(20, 100), 2),
                'currency': currency,
                'payment_method': payment,
                'ip_address': ip,
                'device_id': device,
                'session_length_sec': random.randint(1800, 7200)
            })

        return transactions

    def _grinder_sequence(self, player_id: str) -> List[Dict[str, Any]]:
        """Grinder: 3-6 small purchases over long sessions"""
        num_txns = random.randint(3, 6)
        game = random.choice(self.GAMES)
        currency = random.choice(self.CURRENCIES)
        ip = self._random_ip()
        device = self._generate_id(12)
        payment = random.choice(self.PAYMENT_METHODS)

        transactions = []
        for _ in range(num_txns):
            item_type = random.choice(['currency', 'emote', 'lootbox'])
            transactions.append({
                'player_id': player_id,
                'game_id': game,
                'item_type': item_type,
                'item_name': f"Basic_{item_type}_{random.randint(1, 500)}",
                'amount': round(random.uniform(1.99, 9.99), 2),
                'currency': currency,
                'payment_method': payment,
                'ip_address': ip,
                'device_id': device,
                'session_length_sec': random.randint(3600, 10800)
            })

        return transactions

    # ========================================================================
    # Helper Methods
    # ========================================================================

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