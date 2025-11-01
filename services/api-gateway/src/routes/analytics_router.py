"""
Analytics routes - Time-series and velocity metrics
"""
from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from src.database.session import get_db
from src.repositories.alert_repository import AlertRepository
from src.services.alert_service import AlertService
from src.api.models.api_models import VelocityAnalytics

analytics_router = APIRouter(prefix="/analytics", tags=["analytics"])


# Dependency to get service
async def get_alert_service(db: AsyncSession = Depends(get_db)) -> AlertService:
    """Create alert service with repository"""
    repository = AlertRepository(db)
    return AlertService(repository)


@analytics_router.get("/velocity", response_model=VelocityAnalytics)
async def get_velocity_analytics(
    bucket_size_seconds: int = 5,
    sliding_window_rows: int = 4,
    service: AlertService = Depends(get_alert_service)
):
    """
    Get time-series velocity analytics for graphing

    Query params:
    - bucket_size_seconds: Time bucket size in seconds (default: 5)
    - sliding_window_rows: Number of rows for trend calculation (default: 4)

    Returns:
    - Time-series data points with velocity, avg score, cumulative count, and trend status
    """
    return await service.get_velocity_analytics(bucket_size_seconds, sliding_window_rows)