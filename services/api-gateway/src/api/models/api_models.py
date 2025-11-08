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
    model_config = ConfigDict(protected_namespaces=())

    fraud_score: int
    fraud_type: str = "unknown"  # ✅ ADD THIS
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



# ============================================================================
# ANALYTICS (Dashboard → FastAPI)
# ============================================================================

class VelocityDataPoint(BaseModel):
    """
    Single data point for velocity time-series graph
    Represents aggregated metrics for a time bucket
    """
    domain: str
    x: float  # Time in seconds since start
    y1_velocity: int  # Number of alerts in this bucket
    y2_avg_score: float  # Average fraud score in this bucket
    y3_cumulative: int  # Cumulative total alerts
    trend_status: str  # BASELINE | SPIKE | TRENDING_UP | STABLE


class VelocityAnalytics(BaseModel):
    """
    Time-series analytics for alert velocity graphing
    Contains data points for multiple domains
    """
    data_points: List[VelocityDataPoint]
    bucket_size_seconds: int
    total_alerts: int
    domains: List[str]