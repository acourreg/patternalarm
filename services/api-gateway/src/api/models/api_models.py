"""
FastAPI-specific models
Use feature_store entities for predictions
"""

from abc import ABC, abstractmethod
from datetime import datetime
from typing import List

from pydantic import BaseModel

# ✅ Import Kafka models (generated)
from src.database.models.alert import Alert
from src.database.models.transaction import TransactionEvent

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
    request_id: str = None

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


# ============================================================================
# Alert Models (Dashboard/API)
# ============================================================================

class AlertDetail(BaseModel):
    """
    Extended alert with nested transactions
    Composition: Kafka Alert + TransactionEvents
    """
    alert: Alert  # ✅ From Kafka models
    transactions: List[TransactionEvent]  # ✅ From Kafka models


class AlertsResponse(BaseModel):
    """Paginated alerts list"""
    alerts: List[Alert]
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