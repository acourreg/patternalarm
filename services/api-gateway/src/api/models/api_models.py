"""
Manual API Models (NOT auto-generated)
These models are for FastAPI endpoints and should be edited manually
"""

from abc import ABC, abstractmethod
from datetime import datetime
from typing import List

from pydantic import BaseModel

from .alert import Alert
from .transactionevent import TransactionEvent


# src/api/models/api_models.py


# ============================================================================
# Base Model
# ============================================================================

class BaseFraudModel(ABC):
    """Abstract base for fraud models"""

    @property
    @abstractmethod
    def ml_version(self) -> str:
        pass

    @abstractmethod
    async def predict_single(self, request: 'PredictRequest') -> 'PredictResponse':
        pass

    @abstractmethod
    async def predict_batch(self, request: 'BatchPredictRequest') -> 'BatchPredictResponse':
        pass

    @abstractmethod
    def health_check(self) -> dict:
        pass


# ============================================================================
# Request/Response Models
# ============================================================================

class Transaction(BaseModel):
    transaction_id: str
    amount: float
    currency: str
    timestamp: str
    payment_method: str
    domain: str
    session_length_sec: int = 0
    country_from: str = None
    country_to: str = None


class PredictRequest(BaseModel):
    """Single actor with grouped transactions"""
    actor_id: str
    domain: str
    transactions: List[Transaction]
    request_id: str = None


class PredictResponse(BaseModel):
    """Single fraud prediction for one actor"""
    actor_id: str
    fraud_type: str
    is_fraud: bool
    confidence: float
    transactions_analyzed: int
    total_amount: float
    time_window_sec: float
    ml_version: str
    inference_time_ms: float


class BatchPredictRequest(BaseModel):
    """Multiple actors for batch prediction"""
    predictions: List[PredictRequest]
    request_id: str = None


class BatchPredictResponse(BaseModel):
    """Batch prediction results"""
    predictions: List[PredictResponse]
    ml_version: str
    total_inference_time_ms: float
    actors_analyzed: int


# ============================================================================
# Health Check
# ============================================================================

class HealthResponse(BaseModel):
    status: str
    database: str
    redis: str
    is_ml_loaded: bool
    ml_version: str
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