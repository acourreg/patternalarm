"""
Alert Routes - REST API controllers
Uses service layer for business logic
"""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession
from typing import Optional

from src.database.postgres_client import get_db
from src.repositories.alert_repository import AlertRepository
from src.services.alert_service import AlertService
from src.api.models.api_models import AlertsResponse, AlertDetail


# Routers
alerts_router = APIRouter(prefix="/alerts", tags=["alerts"])



# Dependency to get service
async def get_alert_service(db: AsyncSession = Depends(get_db)) -> AlertService:
    """Create alert service with repository"""
    repository = AlertRepository(db)
    return AlertService(repository)


# ============================================================================
# ALERT ENDPOINTS
# ============================================================================

@alerts_router.get("", response_model=AlertsResponse)
async def get_alerts(
    domain: Optional[str] = None,
    severity: Optional[str] = None,
    limit: int = 10,
    page: int = 1,
    service: AlertService = Depends(get_alert_service)
):
    """
    Get paginated list of alerts with optional filters
    
    Query params:
    - domain: Filter by domain (gaming, ecommerce, fintech)
    - severity: Filter by severity (CRITICAL, HIGH, MEDIUM, LOW)
    - limit: Items per page (default: 10)
    - page: Page number (default: 1)
    """
    return await service.get_alerts(domain, severity, limit, page)


@alerts_router.get("/{alert_id}", response_model=AlertDetail)
async def get_alert_detail(
    alert_id: int,
    service: AlertService = Depends(get_alert_service)
):
    """
    Get alert detail with nested transactions
    
    Path params:
    - alert_id: Alert ID
    """
    alert_detail = await service.get_alert_detail(alert_id)
    
    if not alert_detail:
        raise HTTPException(status_code=404, detail=f"Alert {alert_id} not found")
    
    return alert_detail

