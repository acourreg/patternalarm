"""
Mappers to convert SQLAlchemy models → Pydantic API models
DB uses snake_case, API uses camelCase
"""
from typing import List
from src.database.models.alert import FraudAlert
from src.database.models.transaction import SuspiciousTransaction
from src.api.models.alert import Alert
from src.api.models.transactionevent import TransactionEvent


def map_transaction_to_api(db_transaction: SuspiciousTransaction) -> TransactionEvent:
    """Convert DB SuspiciousTransaction → API TransactionEvent"""
    return TransactionEvent(
        transactionId=db_transaction.transaction_id,
        domain=db_transaction.domain,
        testId=db_transaction.test_id or "",
        timestamp=db_transaction.timestamp,
        actorId=db_transaction.actor_id,
        amount=db_transaction.amount,
        currency=db_transaction.currency or "USD",
        ipAddress=db_transaction.ip_address or "",
        pattern=db_transaction.pattern or "",
        isFraud=db_transaction.is_fraud or False,
        sequencePosition=db_transaction.sequence_position or 0,
        # Gaming fields
        playerId=db_transaction.player_id,
        gameId=db_transaction.game_id,
        itemType=db_transaction.item_type,
        itemName=db_transaction.item_name,
        paymentMethod=db_transaction.payment_method,
        deviceId=db_transaction.device_id,
        sessionLengthSec=db_transaction.session_length_sec,
        # Fintech fields
        accountFrom=db_transaction.account_from,
        accountTo=db_transaction.account_to,
        transferType=db_transaction.transfer_type,
        countryFrom=db_transaction.country_from,
        countryTo=db_transaction.country_to,
        purpose=db_transaction.purpose,
        # Ecommerce fields
        userId=db_transaction.user_id,
        cartItems=db_transaction.cart_items,
        shippingAddress=db_transaction.shipping_address,
        billingAddress=db_transaction.billing_address,
        deviceFingerprint=db_transaction.device_fingerprint,
        sessionDurationSec=db_transaction.session_duration_sec
    )


def map_alert_to_api(db_alert: FraudAlert) -> Alert:
    """Convert DB FraudAlert → API Alert"""
    return Alert(
        alertId=db_alert.alert_id,
        alertType=db_alert.alert_type,
        domain=db_alert.domain,
        actorId=db_alert.actor_id,
        severity=db_alert.severity,
        fraudScore=db_alert.fraud_score,
        transactionCount=db_alert.transaction_count,
        totalAmount=db_alert.total_amount,
        firstSeen=db_alert.first_seen,
        lastSeen=db_alert.last_seen,
        windowSeconds=db_alert.window_seconds,
        baselineAvg=db_alert.baseline_avg,
        patternsDetected=db_alert.patterns_detected,
        confidence=db_alert.confidence,
        modelVersion=db_alert.model_version,
        inferenceTimeMs=db_alert.inference_time_ms,
        # Gaming fields
        playerId=db_alert.player_id,
        gameId=db_alert.game_id,
        itemType=db_alert.item_type,
        itemName=db_alert.item_name,
        sessionLengthSec=db_alert.session_length_sec,
        # Fintech fields
        accountFrom=db_alert.account_from,
        accountTo=db_alert.account_to,
        transferType=db_alert.transfer_type,
        countryFrom=db_alert.country_from,
        countryTo=db_alert.country_to,
        purpose=db_alert.purpose,
        # Ecommerce fields
        userId=db_alert.user_id,
        cartItems=db_alert.cart_items,
        shippingAddress=db_alert.shipping_address,
        billingAddress=db_alert.billing_address,
        deviceFingerprint=db_alert.device_fingerprint,
        sessionDurationSec=db_alert.session_duration_sec,
        paymentMethod=db_alert.payment_method,
        deviceId=db_alert.device_id,
        ipAddress=db_alert.ip_address
    )


def map_alert_with_transactions(db_alert: FraudAlert) -> tuple[Alert, List[TransactionEvent]]:
    """
    Convert FraudAlert + nested SuspiciousTransactions → Alert + List[TransactionEvent]
    Returns tuple (alert, transactions) for AlertDetail composition
    """
    alert = map_alert_to_api(db_alert)
    transactions = [map_transaction_to_api(t) for t in db_alert.transactions]
    return alert, transactions
