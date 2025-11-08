"""
Feature Engineering Logic
SINGLE SOURCE OF TRUTH for features
Used by both:
- Training (Airflow/Spark notebooks)
- Serving (FastAPI)
"""

from typing import Dict, List
from datetime import datetime
import pandas as pd

from contants import (
    HIGH_RISK_COUNTRIES,
    KNOWN_PAYMENT_METHODS,
    NEAR_THRESHOLD_MIN,
    NEAR_THRESHOLD_MAX,
    RAPID_SESSION_THRESHOLD_SEC,
    NIGHT_HOUR_START,
    NIGHT_HOUR_END
)
from .entities import ActorTransactions


class FeatureEngineering:
    """
    Feature engineering for fraud detection
    Supports both Spark (training) and Pandas (serving)
    """

    @staticmethod
    def extract_features_pandas(actor: ActorTransactions) -> Dict:
        """
        Extract features for single actor (FastAPI serving)

        Args:
            actor: ActorTransactions with grouped transactions

        Returns:
            Dict of computed features
        """
        txns = [t.model_dump() for t in actor.transactions]

        # Aggregate metrics
        amounts = [t['amount'] for t in txns]
        sessions = [t.get('session_length_sec', 0) for t in txns]
        timestamps = [
            datetime.fromisoformat(t['timestamp'])
            if isinstance(t['timestamp'], str)
            else t['timestamp']
            for t in txns
        ]

        total_amount = sum(amounts)
        avg_session = sum(sessions) / len(sessions) if sessions else 0
        fraud_txn_count = len(txns)
        time_delta_sec = (max(timestamps) - min(timestamps)).total_seconds()

        # First transaction
        first_txn = txns[0]
        first_ts = timestamps[0]

        # Country analysis
        country_mismatch = any(
            t.get('country_from') and t.get('country_to')
            and t['country_from'] != t['country_to']
            for t in txns
        )

        involves_high_risk = any(
            t.get('country_from') in HIGH_RISK_COUNTRIES or
            t.get('country_to') in HIGH_RISK_COUNTRIES
            for t in txns
        )

        # Payment method normalization
        payment = first_txn.get('payment_method', 'credit_card')
        if payment not in KNOWN_PAYMENT_METHODS:
            payment = 'credit_card'

        # Temporal features
        hour = first_ts.hour
        day_of_week = first_ts.isoweekday()

        return {
            # Base features
            "amount": total_amount,
            "fraud_txn_count": fraud_txn_count,
            "session_length_sec": avg_session,

            # Binary features
            "country_mismatch": 1 if country_mismatch else 0,
            "involves_high_risk_country": 1 if involves_high_risk else 0,
            "is_near_threshold": 1 if NEAR_THRESHOLD_MIN <= total_amount < NEAR_THRESHOLD_MAX else 0,
            "is_rapid_session": 1 if avg_session < RAPID_SESSION_THRESHOLD_SEC else 0,
            "is_weekend": 1 if day_of_week in [6, 7] else 0,
            "night_rapid_combo": 1 if ((
                                                   hour < NIGHT_HOUR_END or hour > NIGHT_HOUR_START) and avg_session < RAPID_SESSION_THRESHOLD_SEC) else 0,

            # Temporal features
            "hour_of_day": hour,
            "day_of_week": day_of_week,

            # Derived features
            "amount_per_txn": total_amount / fraud_txn_count,
            "session_efficiency": total_amount / (avg_session + 1),

            # Categorical
            "payment_method": payment,
            "domain": actor.domain,

            # Metadata
            "fraud_first_seen": first_ts,
            "time_delta_sec": time_delta_sec
        }

    @staticmethod
    def get_spark_transformations():
        """
        Returns PySpark transformations for training
        Use this in Spark notebooks

        Returns:
            Function that takes SparkDataFrame and returns transformed SparkDataFrame
        """
        from pyspark.sql.functions import (
            col, when, hour, dayofweek, lit, to_timestamp
        )

        def transform(df):
            """Apply all feature transformations to Spark DataFrame"""

            # Fix timestamp
            df = df.withColumn("timestamp", to_timestamp(col("timestamp")))

            # Country features
            df = df.withColumn(
                "country_mismatch",
                when(
                    col("country_from").isNotNull() & col("country_to").isNotNull(),
                    (col("country_from") != col("country_to")).cast("int")
                ).otherwise(0)
            )

            df = df.withColumn(
                "involves_high_risk_country",
                when(
                    col("country_to").isNotNull() | col("country_from").isNotNull(),
                    (col("country_to").isin(HIGH_RISK_COUNTRIES) |
                     col("country_from").isin(HIGH_RISK_COUNTRIES)).cast("int")
                ).otherwise(0)
            )

            # Temporal features
            df = df.withColumn("hour_of_day", hour(col("fraud_first_seen")))
            df = df.withColumn("day_of_week", dayofweek(col("fraud_first_seen")))
            df = df.withColumn(
                "is_weekend",
                (dayofweek(col("fraud_first_seen")).isin([1, 7])).cast("int")
            )

            # Threshold features
            df = df.withColumn(
                "is_near_threshold",
                ((col("amount") >= NEAR_THRESHOLD_MIN) &
                 (col("amount") < NEAR_THRESHOLD_MAX)).cast("int")
            )

            df = df.withColumn(
                "is_rapid_session",
                when(
                    col("session_length_sec").isNotNull(),
                    (col("session_length_sec") < RAPID_SESSION_THRESHOLD_SEC).cast("int")
                ).otherwise(0)
            )

            # Derived features
            df = df.withColumn(
                "amount_per_txn",
                col("amount") / (col("fraud_txn_count") + 1)
            )

            df = df.withColumn(
                "session_efficiency",
                col("amount") / (col("session_length_sec") + 1)
            )

            df = df.withColumn(
                "night_rapid_combo",
                (((col("hour_of_day") < NIGHT_HOUR_END) |
                  (col("hour_of_day") > NIGHT_HOUR_START)) &
                 (col("is_rapid_session") == 1)).cast("int")
            )

            # Simplify fraud patterns
            df = df.withColumn(
                "fraud_pattern_simplified",
                when(col("fraud_pattern").startswith("regular_"), lit("regular"))
                .otherwise(col("fraud_pattern"))
            )

            # Fill nulls
            df = df.na.fill({
                "session_length_sec": 0,
                "payment_method": "unknown",
                "hour_of_day": 12,
                "day_of_week": 3,
                "is_weekend": 0,
                "is_near_threshold": 0,
                "involves_high_risk_country": 0,
                "is_rapid_session": 0,
                "amount_per_txn": 0,
                "session_efficiency": 0,
                "night_rapid_combo": 0,
                "country_mismatch": 0
            })

            return df

        return transform

    @staticmethod
    def get_feature_columns() -> List[str]:
        """
        Returns list of feature column names
        Used by both training and serving pipelines
        """
        return [
            "amount",
            "fraud_txn_count",
            "session_length_sec",
            "country_mismatch",
            "hour_of_day",
            "day_of_week",
            "is_weekend",
            "is_near_threshold",
            "involves_high_risk_country",
            "is_rapid_session",
            "amount_per_txn",
            "session_efficiency",
            "night_rapid_combo",
            "payment_idx",  # Will be created by StringIndexer
            "domain_idx"  # Will be created by StringIndexer
        ]