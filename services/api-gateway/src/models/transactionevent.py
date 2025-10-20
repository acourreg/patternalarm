from pydantic import BaseModel
from typing import Optional, List
from datetime import datetime

class TransactionEvent(BaseModel):
    """Raw transaction event from Kafka. Supports all 3 domains: gaming, fintech, ecommerce"""
    transactionId: str
    domain: str
    testId: str
    timestamp: datetime
    actorId: str
    amount: float
    currency: str
    ipAddress: str
    pattern: str
    isFraud: bool
    sequencePosition: int
    playerId: Optional[str] = None
    gameId: Optional[str] = None
    itemType: Optional[str] = None
    itemName: Optional[str] = None
    paymentMethod: Optional[str] = None
    deviceId: Optional[str] = None
    sessionLengthSec: Optional[int] = None
    accountFrom: Optional[str] = None
    accountTo: Optional[str] = None
    transferType: Optional[str] = None
    countryFrom: Optional[str] = None
    countryTo: Optional[str] = None
    purpose: Optional[str] = None
    userId: Optional[str] = None
    cartItems: Optional[str] = None
    shippingAddress: Optional[str] = None
    billingAddress: Optional[str] = None
    deviceFingerprint: Optional[str] = None
    sessionDurationSec: Optional[int] = None
