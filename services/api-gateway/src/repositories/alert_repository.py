"""
Alert Repository - Data access layer
Minimal implementation for get_alerts() and get_alert_by_id()
"""
from typing import List, Optional, Tuple
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, text
from src.database.models.alert import DBAlert


class AlertRepository:
    """Repository for fraud alerts database operations"""

    def __init__(self, session: AsyncSession):
        self.session = session

    async def get_alerts(
        self,
        domain: Optional[str] = None,
        severity: Optional[str] = None,
        limit: int = 10,
        page: int = 1
    ) -> Tuple[List[DBAlert], int]:
        """
        Get paginated list of alerts with filters
        Returns: (alerts, total_count)
        """
        # Build base query
        query = select(DBAlert)
        
        # Apply filters
        if domain:
            query = query.where(DBAlert.domain == domain)
        if severity:
            query = query.where(DBAlert.severity == severity)

        # Get total count for pagination
        count_query = select(func.count()).select_from(query.subquery())
        total_result = await self.session.execute(count_query)
        total = total_result.scalar_one()

        # Apply pagination and ordering
        offset = (page - 1) * limit
        query = query.order_by(DBAlert.last_seen.desc()).limit(limit).offset(offset)

        # Execute query
        result = await self.session.execute(query)
        alerts = result.scalars().all()

        return list(alerts), total

    async def get_alert_by_id(self, alert_id: int) -> Optional[DBAlert]:
        """
        Get alert by ID with nested transactions
        Relationship loading is handled by lazy="selectin" in model
        """
        query = select(DBAlert).where(DBAlert.alert_id == alert_id)
        result = await self.session.execute(query)
        return result.scalar_one_or_none()

    async def get_velocity_analytics(
            self,
            bucket_size_seconds: int = 5,
            sliding_window_rows: int = 4
    ) -> List[dict]:
        """
        Get time-series analytics for graphing alert velocity and trends

        Args:
            bucket_size_seconds: Time bucket size in seconds (default: 5)
            sliding_window_rows: Number of previous rows for trend calculation (default: 4)

        Returns:
            List of data points for graphing with x, y1_velocity, y2_avg_score, y3_cumulative, trend_status
        """
        query = text("""
                     WITH params AS (SELECT CAST(:bucket_size AS INTEGER)  as bucket_size_seconds,
                                            CAST(:sliding_rows AS INTEGER) as sliding_window_rows),
                          global_min AS (SELECT MIN(created_at) as start_time
                                         FROM fraud_alerts),
                          time_buckets AS (SELECT fa.domain,
                                                  FLOOR(EXTRACT(EPOCH FROM (fa.created_at - gm.start_time))::numeric /
                          (SELECT bucket_size_seconds FROM params)::numeric) *
                                                  (SELECT bucket_size_seconds FROM params) as bucket_seconds,
                                                  COUNT(*)                                 as alerts_in_bucket,
                                                  AVG(fa.fraud_score)                      as avg_score_in_bucket,
                                                  MIN(fa.created_at)                       as bucket_start,
                                                  MAX(fa.created_at)                       as bucket_end
                                           FROM fraud_alerts fa
                                                    CROSS JOIN global_min gm
                                           GROUP BY fa.domain, bucket_seconds),
                          with_velocity AS (SELECT
                         domain, bucket_seconds as x, alerts_in_bucket as y1_velocity, ROUND(avg_score_in_bucket:: numeric, 1) as y2_avg_score, SUM (alerts_in_bucket) OVER (
                         PARTITION BY domain
                         ORDER BY bucket_seconds
                         ) as cumulative_alerts, LAG(alerts_in_bucket, 1) OVER (
                         PARTITION BY domain
                         ORDER BY bucket_seconds
                         ) as prev_velocity
                     FROM time_buckets
                         )
                     SELECT
                         domain, x, y1_velocity, y2_avg_score, cumulative_alerts as y3_cumulative, CASE
                         WHEN prev_velocity IS NULL THEN 'BASELINE'
                         WHEN y1_velocity > prev_velocity * 1.5 THEN 'SPIKE'
                         WHEN y1_velocity > prev_velocity * 1.1 THEN 'TRENDING_UP'
                         ELSE 'STABLE'
                     END
                     as trend_status
            FROM with_velocity
            ORDER BY domain, x
                     """)

        result = await self.session.execute(
            query,
            {
                "bucket_size": bucket_size_seconds,
                "sliding_rows": sliding_window_rows
            }
        )

        rows = result.fetchall()

        # Convert to list of dicts
        return [
            {
                "domain": row.domain,
                "x": float(row.x),
                "y1_velocity": int(row.y1_velocity),
                "y2_avg_score": float(row.y2_avg_score),
                "y3_cumulative": int(row.y3_cumulative),
                "trend_status": row.trend_status
            }
            for row in rows
        ]
