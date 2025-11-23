"""
Health check route
"""
from fastapi import APIRouter
from datetime import datetime

from src.api.models.api_models import HealthResponse
from src.ml.spark_fraud_model import SparkFraudModel

health_router = APIRouter(tags=["health"])


@health_router.get("/health", response_model=HealthResponse)
@health_router.get("/health", response_model=HealthResponse)
async def health():
    """System health check"""
    return HealthResponse(
        status="healthy",
        database="connected",  # TODO: Real DB check
        redis="connected",     # TODO: Real Redis check
        is_ml_loaded=True,     # Model loaded at startup
        ml_version="spark-v1.0",
        timestamp=datetime.now()
    )