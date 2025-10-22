"""
Manual API Models (NOT auto-generated)
These models are for FastAPI endpoints and should be edited manually
"""

from typing import List, Dict, Optional
from pydantic import BaseModel, ConfigDict
from datetime import datetime
from .alert import Alert
from .transactionevent import TransactionEvent


# ============================================================================
# MODEL SERVING (Flink → FastAPI)
# ============================================================================

class PredictRequest(BaseModel):
    """
    ML prediction request from Flink processor
    Contains aggregated window data + raw transactions
    """
    model_config = ConfigDict(protected_namespaces=())  # ✅ Fix pydantic warning

    actor_id: str
    domain: str
    transaction_count: int
    total_amount: float
    time_delta_sec: int
    window_start: datetime
    window_end: datetime
    transactions: List[TransactionEvent]


class PredictResponse(BaseModel):
    """ML model prediction response"""
    model_config = ConfigDict(protected_namespaces=())  # ✅ Fix pydantic warning

    fraud_score: int
    model_version: str = "v1.0-mocked"
    inference_time_ms: int = 12
    transactions_analyzed: int


# ============================================================================
# QUERY LAYER (Dashboard → FastAPI)
# ============================================================================

class HealthResponse(BaseModel):
    """Health check response"""
    model_config = ConfigDict(protected_namespaces=())  # ✅ Fix pydantic warning

    status: str
    database: str
    redis: str
    model_loaded: bool
    model_version: str
    timestamp: datetime


class AlertDetail(BaseModel):
    """
    Extended alert with nested transactions
    Clean composition: Alert + transactions, no duplication
    """
    alert: Alert
    transactions: List[TransactionEvent]


class AlertsResponse(BaseModel):
    """Paginated list of alerts"""
    alerts: List[Alert]
    total: int
    page: int = 1


class AnalyticsSummary(BaseModel):
    """Overall analytics summary"""
    period: str
    total_alerts: int
    by_severity: Dict[str, int]
    by_domain: Dict[str, int]
    avg_fraud_score: int
    total_amount_flagged: float


class DomainAnalytics(BaseModel):
    """Domain-specific analytics"""
    domain: str
    period: str
    alert_count: int
    avg_fraud_score: int
    total_amount: float
    top_alert_types: List[Dict[str, int]]
    trend: str


class Transaction(BaseModel):
    """
    Simple transaction view for mock data
    (Not the same as TransactionEvent - simplified for API responses)
    """
    transaction_id: str
    actor_id: str
    domain: str
    timestamp: datetime
    amount: float
    transaction_data: Dict