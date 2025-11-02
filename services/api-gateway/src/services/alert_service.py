"""
Alert Service - Business logic layer
"""
from typing import Optional
import logging
from src.repositories.alert_repository import AlertRepository
from src.api.mappers import map_alert_to_api, map_alert_with_transactions
from src.api.models.api_models import AlertDetail, AlertsResponse, VelocityAnalytics, VelocityDataPoint
from src.database.redis_client import RedisClient

logger = logging.getLogger(__name__)


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

    async def get_velocity_analytics(
            self,
            bucket_size_seconds: int = 5,
            sliding_window_rows: int = 4
    ) -> VelocityAnalytics:
        """Get velocity analytics with Redis cache (10s TTL)"""

        # Build cache key
        cache_key = f"analytics:velocity:bucket_{bucket_size_seconds}:rows_{sliding_window_rows}"

        # Try cache first
        cached = await RedisClient.get_cached(cache_key)
        if cached:
            logger.info(f"🎯 CACHE HIT: {cache_key}")
            return VelocityAnalytics(**cached)

        # Cache miss - query database
        logger.info(f"❌ CACHE MISS: {cache_key} - Querying PostgreSQL...")
        data_points_raw = await self.repository.get_velocity_analytics(
            bucket_size_seconds,
            sliding_window_rows
        )

        data_points = [VelocityDataPoint(**dp) for dp in data_points_raw]

        result = VelocityAnalytics(
            data_points=data_points,
            bucket_size_seconds=bucket_size_seconds,
            total_alerts=sum(dp.y1_velocity for dp in data_points),
            domains=list(set(dp.domain for dp in data_points))
        )

        # Store in cache
        await RedisClient.set_cached(cache_key, result, ttl_seconds=10)
        logger.info(f"💾 CACHED: {cache_key} (TTL: 10s)")

        return result
