"""
E-commerce transaction generator - AC2.2 PARTIAL
TODO: Complete the fraud injection methods following gaming.py pattern
"""
import random
from typing import Dict, Any, List
from .base import BaseGenerator, FraudPattern


class EcommerceGenerator(BaseGenerator):
    """Generates e-commerce checkout transactions"""

    # Product catalog
    CATEGORIES = ['electronics', 'clothing', 'home', 'books', 'toys']
    PAYMENT_METHODS = ['credit_card', 'debit_card', 'paypal', 'apple_pay', 'google_pay']

    def __init__(self, test_id: str):
        super().__init__(domain='ecommerce', test_id=test_id)

    def _get_fraud_rates(self) -> Dict[FraudPattern, float]:
        """AC2.2: E-commerce fraud distribution"""
        return {
            FraudPattern.CARD_TESTING: 0.04,  # 4%
            FraudPattern.FRIENDLY_FRAUD: 0.03,  # 3%
            FraudPattern.PROMO_ABUSE: 0.02  # 2%
        }

    def _generate_legitimate_transaction(self) -> Dict[str, Any]:
        """Generate clean e-commerce transaction"""
        user_id = f"U{random.randint(100000, 999999)}"

        # Generate cart items
        cart_items = self._generate_cart_items()
        total_amount = sum(item['price'] * item['quantity'] for item in cart_items)

        return {
            'user_id': user_id,
            'cart_items': cart_items,
            'payment_method': random.choice(self.PAYMENT_METHODS),
            'amount': round(total_amount, 2),
            'currency': 'USD',
            'shipping_address': self._generate_address(),
            'billing_address': self._generate_address(),
            'ip_address': self._random_ip(),
            'session_duration': random.randint(60, 1800),
            'device_fingerprint': self._generate_id(32)
        }

    def _inject_fraud_pattern(self, transaction: Dict[str, Any],
                              pattern: FraudPattern) -> Dict[str, Any]:
        """Inject fraud signals"""

        if pattern == FraudPattern.CARD_TESTING:
            # TODO: Implement card testing pattern
            # Hint: Multiple small transactions, different cards, short intervals
            # Examples from gaming.py:
            # - Modify amount to be very small (< $5)
            # - Add 'card_test_sequence' field
            # - Set session_duration to very short (< 30s)
            transaction['amount'] = random.uniform(0.99, 4.99)
            transaction['card_test_sequence'] = random.randint(1, 20)
            # TODO: Add more card testing signals here

        elif pattern == FraudPattern.FRIENDLY_FRAUD:
            # TODO: Implement friendly fraud pattern
            # Hint: Legitimate purchase but will dispute later
            # - High-value items
            # - Digital goods (harder to prove delivery)
            # - Pattern: customer_type = 'repeat_disputer'
            pass  # Implement this

        elif pattern == FraudPattern.PROMO_ABUSE:
            # TODO: Implement promo abuse pattern
            # Hint: Multiple accounts, same address, promo code abuse
            # - Add 'promo_code_used' field
            # - Mark 'shipping_address' as duplicate
            # - Low account age
            pass  # Implement this

        return transaction

    def _generate_cart_items(self) -> List[Dict[str, Any]]:
        """Generate 1-5 cart items"""
        num_items = random.randint(1, 5)
        items = []

        for _ in range(num_items):
            category = random.choice(self.CATEGORIES)
            items.append({
                'product_id': f"PROD{random.randint(1000, 9999)}",
                'category': category,
                'price': self._get_product_price(category),
                'quantity': random.randint(1, 3)
            })

        return items

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

    @staticmethod
    def _generate_address() -> Dict[str, str]:
        """
        Generate realistic shipping/billing address
        TODO: Make this more realistic with real street patterns
        """
        streets = ['Main St', 'Oak Ave', 'Park Blvd', 'Elm St', 'Maple Dr']
        cities = ['New York', 'Los Angeles', 'Chicago', 'Houston', 'Phoenix']
        states = ['NY', 'CA', 'IL', 'TX', 'AZ']

        return {
            'street': f"{random.randint(100, 9999)} {random.choice(streets)}",
            'city': random.choice(cities),
            'state': random.choice(states),
            'zip': f"{random.randint(10000, 99999)}",
            'country': 'US'
        }