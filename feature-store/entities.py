"""
Shared entities between training and serving
Maps to Kafka TransactionEvent schema
"""

from typing import Optional, List
from pydantic import BaseModel
from datetime import datetime


class Transaction(BaseModel):
    """
    Single transaction entity
    Maps to TransactionEvent from Kafka
    """
    transaction_id: str
    amount: float
    currency: str
    timestamp: str  # ISO format
    payment_method: str
    domain: str
    session_length_sec: int = 0
    country_from: Optional[str] = None
    country_to: Optional[str] = None


class ActorTransactions(BaseModel):
    """
    Grouped transactions for a single actor
    This is the unit of prediction
    """
    actor_id: str
    domain: str
    transactions: List[Transaction]

    # Computed fields (set during feature engineering)
    fraud_first_seen: Optional[datetime] = None
    fraud_txn_count: Optional[int] = None
    total_amount: Optional[float] = None