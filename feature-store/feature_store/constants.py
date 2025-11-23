"""
Shared constants across training and serving
"""

# High-risk countries for fraud detection
HIGH_RISK_COUNTRIES = ['KY', 'PA', 'BZ', 'VG']

# Known payment methods
KNOWN_PAYMENT_METHODS = [
    'credit_card',
    'debit_card',
    'paypal',
    'apple_pay',
    'google_pay'
]

# Fraud detection thresholds
NEAR_THRESHOLD_MIN = 9500
NEAR_THRESHOLD_MAX = 10000

# Session thresholds
RAPID_SESSION_THRESHOLD_SEC = 120

# Time windows
NIGHT_HOUR_START = 22
NIGHT_HOUR_END = 6

# Fraud pattern labels (simplified)
FRAUD_LABELS = [
    "regular",
    "fraud_account_takeover",
    "fraud_card_testing",
    "fraud_chargeback_fraud",
    "fraud_structuring",
    "fraud_money_laundering",
    "fraud_bonus_abuse",
    "fraud_item_duplication"
]