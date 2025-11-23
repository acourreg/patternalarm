"""
Mappers to convert between DB models and API models
DB (SQLAlchemy) → API (Pydantic)
"""
from typing import List, Tuple
from src.database.models.alert import DBAlert
from src.database.models.transaction import DBTransaction
from src.api.models.alert import Alert
from src.api.models.transactionevent import TransactionEvent
from src.api.models.api_models import AlertResponse


def map_transaction_to_api(db_txn: DBTransaction) -> TransactionEvent:
    """
    Convert DBTransaction (SQLAlchemy) → TransactionEvent (Pydantic/Kafka)
    """
    return TransactionEvent(
        transactionId=db_txn.transaction_id,
        domain=db_txn.domain,
        testId=db_txn.test_id,
        timestamp=db_txn.timestamp,
        actorId=db_txn.actor_id,
        amount=db_txn.amount,
        currency=db_txn.currency,
        ipAddress=db_txn.ip_address,
        pattern=db_txn.pattern,
        isFraud=db_txn.is_fraud,
        sequencePosition=db_txn.sequence_position,
        # Gaming fields (optional)
        playerId=db_txn.player_id,
        gameId=db_txn.game_id,
        itemType=db_txn.item_type,
        itemName=db_txn.item_name,
        paymentMethod=db_txn.payment_method,
        deviceId=db_txn.device_id,
        sessionLengthSec=db_txn.session_length_sec,
        # Fintech fields (optional)
        accountFrom=db_txn.account_from,
        accountTo=db_txn.account_to,
        transferType=db_txn.transfer_type,
        countryFrom=db_txn.country_from,
        countryTo=db_txn.country_to,
        purpose=db_txn.purpose,
        # Ecommerce fields (optional)
        userId=db_txn.user_id,
        cartItems=db_txn.cart_items,
        shippingAddress=db_txn.shipping_address,
        billingAddress=db_txn.billing_address,
        deviceFingerprint=db_txn.device_fingerprint,
        sessionDurationSec=db_txn.session_duration_sec
    )


def map_alert_to_api(db_alert: DBAlert) -> Alert:
    """
    Convert DBAlert (SQLAlchemy) → Alert (Pydantic/Kafka)
    """
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
        # Optional analytics fields
        windowSeconds=db_alert.window_seconds,
        baselineAvg=db_alert.baseline_avg,
        patternsDetected=db_alert.patterns_detected,
        confidence=db_alert.confidence,
        modelVersion=db_alert.model_version,
        inferenceTimeMs=db_alert.inference_time_ms,
        # Gaming fields (optional)
        playerId=db_alert.player_id,
        gameId=db_alert.game_id,
        itemType=db_alert.item_type,
        itemName=db_alert.item_name,
        sessionLengthSec=db_alert.session_length_sec,
        # Fintech fields (optional)
        accountFrom=db_alert.account_from,
        accountTo=db_alert.account_to,
        transferType=db_alert.transfer_type,
        countryFrom=db_alert.country_from,
        countryTo=db_alert.country_to,
        purpose=db_alert.purpose,
        # Ecommerce fields (optional)
        userId=db_alert.user_id,
        cartItems=db_alert.cart_items,
        shippingAddress=db_alert.shipping_address,
        billingAddress=db_alert.billing_address,
        deviceFingerprint=db_alert.device_fingerprint,
        sessionDurationSec=db_alert.session_duration_sec,
        # Common fields
        paymentMethod=db_alert.payment_method,
        deviceId=db_alert.device_id,
        ipAddress=db_alert.ip_address
    )


def map_alert_to_api(db_alert: DBAlert) -> AlertResponse:
    """
    Convert DBAlert (SQLAlchemy) → AlertResponse (API Pydantic)
    Lighter version without all optional fields
    """
    return AlertResponse.from_db(db_alert)


def map_alert_with_transactions(db_alert: DBAlert) -> Tuple[Alert, List[TransactionEvent]]:
    """
    Convert DBAlert with nested transactions
    Returns: (Alert, List[TransactionEvent]) for AlertDetail

    Note: db_alert.transactions is loaded via relationship (lazy="selectin")
    """
    alert = map_alert_to_api(db_alert)
    transactions = [map_transaction_to_api(txn) for txn in db_alert.transactions]
    return alert, transactions