"""
SQLAlchemy models for fraud detection tables
"""
from datetime import datetime
from sqlalchemy import Column, Integer, String, Float, DateTime, Boolean, ForeignKey, ARRAY, BigInteger
from sqlalchemy.orm import relationship
from src.database.postgres_client import Base


class FraudAlert(Base):
    """Fraud alert model"""
    __tablename__ = "fraud_alerts"

    # ✅ PK s'appelle "alert_id" (SERIAL)
    alert_id = Column(Integer, primary_key=True, autoincrement=True)

    # Core alert fields
    alert_type = Column(String(50), nullable=False)
    domain = Column(String(20), nullable=False)
    actor_id = Column(String(100), nullable=False, index=True)
    severity = Column(String(20), nullable=False, index=True)
    fraud_score = Column(Integer, nullable=False)
    transaction_count = Column(Integer, nullable=False)
    total_amount = Column(Float, nullable=False)  # DECIMAL(15,2)
    first_seen = Column(DateTime, nullable=False)
    last_seen = Column(DateTime, nullable=False)

    # Analytics fields
    window_seconds = Column(BigInteger)
    baseline_avg = Column(Float)  # DOUBLE PRECISION
    patterns_detected = Column(ARRAY(String))  # TEXT[]
    confidence = Column(Integer)
    model_version = Column(String(50))
    inference_time_ms = Column(Integer)

    # Gaming fields
    player_id = Column(String(100))
    game_id = Column(String(100))
    item_type = Column(String(50))
    item_name = Column(String(100))
    session_length_sec = Column(Integer)

    # Fintech fields
    account_from = Column(String(100))
    account_to = Column(String(100))
    transfer_type = Column(String(50))
    country_from = Column(String(10))
    country_to = Column(String(10))
    purpose = Column(String(100))

    # Ecommerce fields
    user_id = Column(String(100))
    cart_items = Column(String)  # TEXT
    shipping_address = Column(String)  # TEXT
    billing_address = Column(String)  # TEXT
    device_fingerprint = Column(String(100))
    session_duration_sec = Column(Integer)

    # Common fields
    payment_method = Column(String(50))
    device_id = Column(String(100))
    ip_address = Column(String(50))

    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    # Relationship
    transactions = relationship("SuspiciousTransaction", back_populates="alert", lazy="selectin")
