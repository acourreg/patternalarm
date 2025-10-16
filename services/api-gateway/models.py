"""
Pydantic models for request/response validation
"""
from typing import List, Dict, Optional
from pydantic import BaseModel
from datetime import datetime


# ============================================================================
# MODEL SERVING (Flink → FastAPI)
# ============================================================================

# ============================================================================
# MODEL SERVING (Flink → FastAPI)
# ============================================================================

class TransactionInput(BaseModel):
    """Individual transaction in the window"""
    transaction_id: str
    timestamp: datetime
    amount: float
    ip_address: Optional[str] = None
    device_id: Optional[str] = None


class PredictRequest(BaseModel):
    """
    Aggregated window features + raw transactions
    Sent by Flink for ML scoring
    """
    # Aggregated features (calculated by Flink)
    actor_id: str
    domain: str
    transaction_count: int
    total_amount: float
    time_delta_sec: int
    window_start: datetime
    window_end: datetime

    # Raw transactions in this window
    transactions: List[TransactionInput]


class PredictResponse(BaseModel):
    """ML model prediction response"""
    fraud_score: int
    model_version: str = "v1.0-mocked"
    inference_time_ms: int = 12
    transactions_analyzed: int  # How many transactions were in the window



# ============================================================================
# QUERY LAYER (Dashboard → FastAPI)
# ============================================================================

class HealthResponse(BaseModel):
    """Health check response"""
    status: str
    database: str
    redis: str
    model_loaded: bool
    model_version: str
    timestamp: datetime


class Transaction(BaseModel):
    """Transaction detail nested in alert"""
    transaction_id: str
    actor_id: str
    domain: str
    timestamp: datetime
    amount: float
    transaction_data: Dict


class Alert(BaseModel):
    """Alert summary"""
    alert_id: int
    alert_type: str
    domain: str
    actor_id: str
    severity: str
    fraud_score: int
    transaction_count: int
    total_amount: float
    first_seen: datetime
    last_seen: datetime


class AlertDetail(Alert):
    """Alert with nested transactions and metadata"""
    transactions: List[Transaction]
    metadata: Dict


class AlertsResponse(BaseModel):
    """List of alerts"""
    alerts: List[Alert]
    total: int
    page: int = 1


class AnalyticsSummary(BaseModel):
    """Overall analytics stats"""
    period: str
    total_alerts: int
    by_severity: Dict[str, int]
    by_domain: Dict[str, int]
    avg_fraud_score: int
    total_amount_flagged: float
    model_version: str = "v1.0-mocked"


class DomainAnalytics(BaseModel):
    """Domain-specific analytics"""
    domain: str
    period: str
    alert_count: int
    avg_fraud_score: int
    total_amount: float
    top_alert_types: List[Dict[str, int]]
    trend: str