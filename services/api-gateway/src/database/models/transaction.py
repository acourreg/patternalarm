"""
SQLAlchemy models for fraud detection tables
"""
from datetime import datetime
from sqlalchemy import Column, Integer, String, Float, DateTime, Boolean, ForeignKey, ARRAY, BigInteger
from sqlalchemy.orm import relationship
from src.database.postgres_client import Base


class SuspiciousTransaction(Base):
    """Suspicious transaction model"""
    __tablename__ = "suspicious_transactions"

    # ✅ Primary key s'appelle "id"
    id = Column(Integer, primary_key=True, autoincrement=True)
    alert_id = Column(Integer, ForeignKey("fraud_alerts.alert_id", ondelete="CASCADE"), nullable=False, index=True)

    # Core transaction fields
    transaction_id = Column(String(100), nullable=False)
    domain = Column(String(20), nullable=False)
    test_id = Column(String(100), nullable=False)
    timestamp = Column(DateTime, nullable=False)
    actor_id = Column(String(100), nullable=False)
    amount = Column(Float, nullable=False)  # DECIMAL(15,2) → Float
    currency = Column(String(10), nullable=False)
    ip_address = Column(String(50), nullable=False)

    pattern = Column(String(50), nullable=False)
    is_fraud = Column(Boolean, nullable=False)
    sequence_position = Column(Integer, nullable=False)

    # Gaming fields
    player_id = Column(String(100))
    game_id = Column(String(100))
    item_type = Column(String(50))
    item_name = Column(String(100))
    payment_method = Column(String(50))
    device_id = Column(String(100))
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

    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    # Relationship
    alert = relationship("FraudAlert", back_populates="transactions")