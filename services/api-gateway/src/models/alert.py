from pydantic import BaseModel
from typing import Optional, List
from datetime import datetime

class Alert(BaseModel):
    """Alert summary (fraud_alerts table)"""
    alertId: int
    alertType: str
    domain: str
    actorId: str
    severity: str
    fraudScore: int
    transactionCount: int
    totalAmount: float
    firstSeen: datetime
    lastSeen: datetime
