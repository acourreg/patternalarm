from typing import List, Dict, Optional
from pydantic import BaseModel, ConfigDict
from datetime import datetime


# ============================================================================
# MODEL SERVING (Flink → FastAPI)
# ============================================================================

class TransactionEvent(BaseModel):
    """
    Full transaction event from Flink
    Contains all fields from Kafka (FastAPI ignores extras it doesn't need)
    """
    # Common fields (all domains)
    transaction_id: str
    timestamp: datetime
    amount: float
    ip_address: str
    
    # Optional domain-specific fields (ignored if not needed)
    device_id: Optional[str] = None
    device_fingerprint: Optional[str] = None
    
    # Config to ignore extra fields from Flink
    model_config = ConfigDict(extra="ignore")


class TimeWindowAggregate(BaseModel):
    """
    Aggregated window data from Flink
    Matches Flink's TimeWindowAggregate case class
    """
    actor_id: str
    domain: str
    transaction_count: int
    total_amount: float
    window_start: datetime
    window_end: datetime
    transactions: List[TransactionEvent]


class PredictRequest(BaseModel):
    """
    Request payload for ML prediction
    Can accept either TimeWindowAggregate or individual fields
    """
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
    fraud_score: int
    model_version: str = "v1.0-mocked"
    inference_time_ms: int = 12
    transactions_analyzed: int


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


class TransactionEvent(BaseModel):
    """
    Raw transaction from Kafka - matches Scala TransactionEvent EXACTLY
    Stored in suspicious_transactions table
    """
    # === Common fields (all domains) ===
    transaction_id: str
    domain: str
    test_id: str
    timestamp: datetime
    actor_id: str
    amount: float
    currency: str
    ip_address: str

    # Pattern metadata
    pattern: str
    is_fraud: bool
    sequence_position: int

    # === Gaming-specific fields ===
    player_id: Optional[str] = None
    game_id: Optional[str] = None
    item_type: Optional[str] = None
    item_name: Optional[str] = None
    payment_method: Optional[str] = None
    device_id: Optional[str] = None
    session_length_sec: Optional[int] = None

    # === Fintech-specific fields ===
    account_from: Optional[str] = None
    account_to: Optional[str] = None
    transfer_type: Optional[str] = None
    country_from: Optional[str] = None
    country_to: Optional[str] = None
    purpose: Optional[str] = None

    # === Ecommerce-specific fields ===
    user_id: Optional[str] = None
    cart_items: Optional[str] = None  # JSON string array
    shipping_address: Optional[str] = None
    billing_address: Optional[str] = None
    device_fingerprint: Optional[str] = None
    session_duration_sec: Optional[int] = None


class Alert(BaseModel):
    """
    Alert summary (first table: fraud_alerts)
    Matches Scala FraudAlert
    """
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
    """
    Alert with embedded transactions and metadata
    Used for API responses with full details
    """
    transactions: List[TransactionEvent]
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
