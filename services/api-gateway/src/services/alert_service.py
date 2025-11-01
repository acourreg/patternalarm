"""
Alert Service - Business logic layer
Minimal implementation for get_alerts() and get_alert_detail()
"""
from typing import Optional
from src.repositories.alert_repository import AlertRepository
from src.api.mappers import map_alert_to_api, map_alert_with_transactions
from src.api.models.api_models import AlertDetail, AlertsResponse, VelocityAnalytics, VelocityDataPoint


class AlertService:
    """Service for alert business logic"""

    def __init__(self, repository: AlertRepository):
        self.repository = repository

    async def get_alerts(
        self,
        domain: Optional[str] = None,
        severity: Optional[str] = None,
        limit: int = 10,
        page: int = 1
    ) -> AlertsResponse:
        """Get paginated alerts with filters"""
        db_alerts, total = await self.repository.get_alerts(domain, severity, limit, page)
        
        # Convert DB models → API models
        api_alerts = [map_alert_to_api(db_alert) for db_alert in db_alerts]
        
        return AlertsResponse(
            alerts=api_alerts,
            total=total,
            page=page
        )

    async def get_alert_detail(self, alert_id: int) -> Optional[AlertDetail]:
        """Get alert with nested transactions"""
        db_alert = await self.repository.get_alert_by_id(alert_id)
        
        if not db_alert:
            return None
        
        # Convert DB models → API models
        alert, transactions = map_alert_with_transactions(db_alert)
        
        return AlertDetail(
            alert=alert,
            transactions=transactions
        )


    # Dans alert_service.py
    async def get_velocity_analytics(
            self,
            bucket_size_seconds: int = 5,
            sliding_window_rows: int = 4
    ) -> VelocityAnalytics:
        """Get velocity analytics for graphing"""
        data_points_raw = await self.repository.get_velocity_analytics(
            bucket_size_seconds,
            sliding_window_rows
        )

        data_points = [VelocityDataPoint(**dp) for dp in data_points_raw]

        return VelocityAnalytics(
            data_points=data_points,
            bucket_size_seconds=bucket_size_seconds,
            total_alerts=sum(dp.y1_velocity for dp in data_points),
            domains=list(set(dp.domain for dp in data_points))
        )