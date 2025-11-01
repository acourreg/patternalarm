"""
Health check route
"""
from fastapi import APIRouter
from datetime import datetime

from src.api.models.api_models import HealthResponse

health_router = APIRouter(tags=["health"])


@health_router.get("/health", response_model=HealthResponse)
async def health():
    """System health check"""
    return HealthResponse(
        status="healthy",
        database="connected",
        redis="connected",
        model_loaded=True,
        model_version="v1.0-mocked",
        timestamp=datetime.now()
    )