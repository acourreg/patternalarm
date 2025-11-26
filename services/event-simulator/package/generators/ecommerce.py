"""
E-commerce transaction generator - Simple and realistic
"""
import random
from typing import Dict, Any, List
from .base import BaseGenerator, TransactionPattern


class EcommerceGenerator(BaseGenerator):
    """Generates e-commerce checkout transactions"""

    CATEGORIES = ['electronics', 'clothing', 'home', 'books', 'toys']
    PAYMENT_METHODS = ['credit_card', 'debit_card', 'paypal', 'apple_pay', 'google_pay']
    STREETS = ['Main St', 'Oak Ave', 'Park Blvd', 'Elm St', 'Maple Dr', 'Pine Rd', 'River Way']
    CITIES = ['New York', 'Los Angeles', 'Chicago', 'Houston', 'Phoenix', 'Miami', 'Seattle']
    STATES = ['NY', 'CA', 'IL', 'TX', 'AZ', 'FL', 'WA']

    def __init__(self, test_id: str):
        super().__init__(test_id=test_id)

    def _generate_pattern_sequence(self, pattern: TransactionPattern, actor_id: str) -> List[Dict[str, Any]]:
        """Generate 2-10 transactions for the given pattern"""

        if pattern == TransactionPattern.FRAUD_CARD_TESTING:
            return self._card_testing_sequence(actor_id)

        elif pattern == TransactionPattern.FRAUD_FRIENDLY_FRAUD:
            return self._friendly_fraud_sequence(actor_id)

        elif pattern == TransactionPattern.FRAUD_PROMO_ABUSE:
            return self._promo_abuse_sequence(actor_id)

        elif pattern == TransactionPattern.REGULAR_SHOPPER:
            return self._regular_shopper_sequence(actor_id)

        elif pattern == TransactionPattern.REGULAR_WINDOW_SHOPPER:
            return self._window_shopper_sequence(actor_id)

        else:
            return self._regular_shopper_sequence(actor_id)

    # ========================================================================
    # Fraud Patterns
    # ========================================================================

    def _card_testing_sequence(self, user_id: str) -> List[Dict[str, Any]]:
        """Card testing: 5-10 small transactions testing stolen cards"""
        num_txns = random.randint(5, 10)
        ip = self._random_ip()
        device = self._generate_id(32)
        shipping = self._generate_address()
        billing = self._generate_address()

        transactions = []
        for _ in range(num_txns):
            # Small test purchases
            item = self._generate_single_item('books')  # Low value items

            transactions.append({
                'user_id': user_id,
                'cart_items': [item],
                'amount': round(item['price'] * item['quantity'], 2),
                'currency': 'USD',
                'payment_method': 'credit_card',
                'shipping_address': shipping,
                'billing_address': billing,
                'ip_address': ip,
                'device_fingerprint': device,
                'session_duration_sec': random.randint(5, 30)
            })

        return transactions

    def _friendly_fraud_sequence(self, user_id: str) -> List[Dict[str, Any]]:
        """Friendly fraud: 2-3 legitimate purchases that will be disputed"""
        num_txns = random.randint(2, 3)
        ip = self._random_ip()
        device = self._generate_id(32)
        shipping = self._generate_address()
        billing = shipping  # Same address (legitimate looking)
        payment = random.choice(self.PAYMENT_METHODS)

        transactions = []
        for _ in range(num_txns):
            # High-value or digital goods
            items = [self._generate_single_item('electronics')]
            total = sum(item['price'] * item['quantity'] for item in items)

            transactions.append({
                'user_id': user_id,
                'cart_items': items,
                'amount': round(total, 2),
                'currency': 'USD',
                'payment_method': payment,
                'shipping_address': shipping,
                'billing_address': billing,
                'ip_address': ip,
                'device_fingerprint': device,
                'session_duration_sec': random.randint(300, 1800)
            })

        return transactions

    def _promo_abuse_sequence(self, user_id: str) -> List[Dict[str, Any]]:
        """Promo abuse: 4-8 orders using same promo code from multiple accounts"""
        num_txns = random.randint(4, 8)
        # Same shipping address (red flag)
        shared_shipping = self._generate_address()
        ip = self._random_ip()

        transactions = []
        for i in range(num_txns):
            # Different device/billing per "account"
            device = self._generate_id(32)
            billing = self._generate_address()
            items = self._generate_cart(1, 2)
            total = sum(item['price'] * item['quantity'] for item in items)

            transactions.append({
                'user_id': f"{user_id}_{i}",  # Multiple fake accounts
                'cart_items': items,
                'amount': round(total, 2),
                'currency': 'USD',
                'payment_method': random.choice(self.PAYMENT_METHODS),
                'shipping_address': shared_shipping,  # Same address!
                'billing_address': billing,
                'ip_address': ip,
                'device_fingerprint': device,
                'session_duration_sec': random.randint(120, 600)
            })

        return transactions

    # ========================================================================
    # Regular Patterns
    # ========================================================================

    def _regular_shopper_sequence(self, user_id: str) -> List[Dict[str, Any]]:
        """Regular shopper: 2-3 normal purchases"""
        num_txns = random.randint(2, 3)
        ip = self._random_ip()
        device = self._generate_id(32)
        shipping = self._generate_address()
        billing = shipping if random.random() > 0.2 else self._generate_address()
        payment = random.choice(self.PAYMENT_METHODS)

        transactions = []
        for _ in range(num_txns):
            items = self._generate_cart(1, 4)
            total = sum(item['price'] * item['quantity'] for item in items)

            transactions.append({
                'user_id': user_id,
                'cart_items': items,
                'amount': round(total, 2),
                'currency': 'USD',
                'payment_method': payment,
                'shipping_address': shipping,
                'billing_address': billing,
                'ip_address': ip,
                'device_fingerprint': device,
                'session_duration_sec': random.randint(300, 2400)
            })

        return transactions

    def _window_shopper_sequence(self, user_id: str) -> List[Dict[str, Any]]:
        """Window shopper: 2-4 small purchases after long browsing"""
        num_txns = random.randint(2, 4)
        ip = self._random_ip()
        device = self._generate_id(32)
        shipping = self._generate_address()
        billing = shipping
        payment = random.choice(self.PAYMENT_METHODS)

        transactions = []
        for _ in range(num_txns):
            # Smaller carts, lower value items
            items = self._generate_cart(1, 2)
            total = sum(item['price'] * item['quantity'] for item in items)

            transactions.append({
                'user_id': user_id,
                'cart_items': items,
                'amount': round(total, 2),
                'currency': 'USD',
                'payment_method': payment,
                'shipping_address': shipping,
                'billing_address': billing,
                'ip_address': ip,
                'device_fingerprint': device,
                'session_duration_sec': random.randint(600, 3600)  # Long browsing
            })

        return transactions

    # ========================================================================
    # Helper Methods
    # ========================================================================

    def _generate_cart(self, min_items: int, max_items: int) -> List[Dict[str, Any]]:
        """Generate cart with multiple items"""
        num_items = random.randint(min_items, max_items)
        items = []

        for _ in range(num_items):
            category = random.choice(self.CATEGORIES)
            items.append(self._generate_single_item(category))

        return items

    def _generate_single_item(self, category: str) -> Dict[str, Any]:
        """Generate a single cart item"""
        return {
            'product_id': f"PROD{random.randint(1000, 9999)}",
            'category': category,
            'price': self._get_product_price(category),
            'quantity': random.randint(1, 3)
        }

    @staticmethod
    def _get_product_price(category: str) -> float:
        """Realistic pricing by category"""
        prices = {
            'electronics': (49.99, 999.99),
            'clothing': (19.99, 199.99),
            'home': (29.99, 499.99),
            'books': (9.99, 49.99),
            'toys': (14.99, 99.99)
        }
        min_price, max_price = prices.get(category, (9.99, 99.99))
        return round(random.uniform(min_price, max_price), 2)

    def _generate_address(self) -> Dict[str, str]:
        """Generate realistic address"""
        return {
            'street': f"{random.randint(100, 9999)} {random.choice(self.STREETS)}",
            'city': random.choice(self.CITIES),
            'state': random.choice(self.STATES),
            'zip': f"{random.randint(10000, 99999)}",
            'country': 'US'
        }