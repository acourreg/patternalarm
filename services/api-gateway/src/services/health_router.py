# src/api/routes/health_router.py

from fastapi import APIRouter
from datetime import datetime
from src.api.models.api_models import HealthResponse

health_router = APIRouter(tags=["health"])

@health_router.get("/health", response_model=HealthResponse)
async def health_check():
    return HealthResponse(
        status="healthy",
        database="connected",
        redis="connected",
        is_ml_loaded=True,  # ✅ Must match HealthResponse model
        ml_version="spark-v1.0",
        timestamp=datetime.now()
    )