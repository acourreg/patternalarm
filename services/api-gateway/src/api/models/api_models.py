# services/api-gateway/src/api/models/api_models.py

from abc import ABC, abstractmethod
from datetime import datetime
from typing import List, Optional

from pydantic import BaseModel, ConfigDict

# ✅ Import Kafka Pydantic models (for validation)
from src.api.models.alert import Alert
from src.api.models.transactionevent import TransactionEvent

# ✅ Import feature store entities
from feature_store.entities import Transaction, ActorTransactions


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
# Request/Response Models (API Layer)
# ============================================================================

class PredictRequest(BaseModel):
    """
    API request for fraud prediction
    Maps to ActorTransactions from feature store
    """
    actor_id: str
    domain: str
    transactions: List[Transaction]  # ✅ From feature_store
    request_id: Optional[str] = None

    def to_actor_transactions(self) -> ActorTransactions:
        """Convert to feature store entity"""
        return ActorTransactions(
            actor_id=self.actor_id,
            domain=self.domain,
            transactions=self.transactions
        )


class PredictResponse(BaseModel):
    """Single fraud prediction result"""
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
    """Batch prediction for multiple actors"""
    predictions: List[PredictRequest]
    request_id: Optional[str] = None


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


# ============================================================================
# Alert Models (Dashboard/API) - Pydantic wrappers for DB models
# ============================================================================

class AlertDetail(BaseModel):
    """
    Extended alert with nested transactions
    Converts SQLAlchemy DBAlert + DBTransaction → Pydantic
    """
    model_config = ConfigDict(from_attributes=True)  # ✅ Enable ORM mode

    alert: Alert  # ✅ Pydantic from Kafka schema
    transactions: List[TransactionEvent]  # ✅ Pydantic from Kafka schema


class AlertResponse(BaseModel):
    """
    Single alert response (wraps DBAlert)
    """
    model_config = ConfigDict(from_attributes=True)

    # Map DBAlert fields
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
    created_at: datetime

    # Optional fields
    window_seconds: Optional[int] = None
    baseline_avg: Optional[float] = None
    patterns_detected: Optional[List[str]] = None
    confidence: Optional[int] = None
    model_version: Optional[str] = None

    @classmethod
    def from_db(cls, db_alert):
        """Convert DBAlert → AlertResponse"""
        return cls(
            alert_id=db_alert.alert_id,
            alert_type=db_alert.alert_type,
            domain=db_alert.domain,
            actor_id=db_alert.actor_id,
            severity=db_alert.severity,
            fraud_score=db_alert.fraud_score,
            transaction_count=db_alert.transaction_count,
            total_amount=db_alert.total_amount,
            first_seen=db_alert.first_seen,
            last_seen=db_alert.last_seen,
            created_at=db_alert.created_at,
            window_seconds=db_alert.window_seconds,
            baseline_avg=db_alert.baseline_avg,
            patterns_detected=db_alert.patterns_detected,
            confidence=db_alert.confidence,
            model_version=db_alert.model_version
        )


class AlertsResponse(BaseModel):
    """Paginated alerts list"""
    alerts: List[AlertResponse]  # ✅ Pydantic wrapper
    total: int
    page: int = 1


# ============================================================================
# Analytics (Dashboard)
# ============================================================================

class VelocityDataPoint(BaseModel):
    """Time-series data point for velocity graph"""
    domain: str
    x: float  # Time in seconds since start
    y1_velocity: int  # Number of alerts
    y2_avg_score: float  # Average fraud score
    y3_cumulative: int  # Cumulative total
    trend_status: str  # BASELINE | SPIKE | TRENDING_UP | STABLE


class VelocityAnalytics(BaseModel):
    """Time-series analytics for alert velocity"""
    data_points: List[VelocityDataPoint]
    bucket_size_seconds: int
    total_alerts: int
    domains: List[str]