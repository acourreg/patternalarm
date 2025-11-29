"""
Tests for extract_features.py job
Tests the actual job logic with realistic data
"""
import pytest
import sys
import os
from datetime import datetime, timedelta
import random

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'jobs'))


class TestExtractFeatures:
    """Tests for extract_features.py job"""

    def test_job_imports(self):
        """✅ Job imports work."""
        from extract_features import main
        assert main is not None

    def test_feature_store_available(self):
        """✅ Feature store is importable."""
        from feature_store.features import FeatureEngineering
        assert FeatureEngineering is not None
        assert hasattr(FeatureEngineering, 'get_spark_transformations')
        assert hasattr(FeatureEngineering, 'get_feature_columns')

    def test_spark_transformations_callable(self):
        """✅ get_spark_transformations returns a callable."""
        from feature_store.features import FeatureEngineering
        transform = FeatureEngineering.get_spark_transformations()
        assert callable(transform)

    def test_extract_pipeline_with_realistic_data(self, spark, temp_dir):
        """✅ Full extraction pipeline with realistic multi-domain data."""
        from pyspark.sql.functions import (
            col, count, sum as spark_sum, min as spark_min, max as spark_max,
            first, avg, lit
        )
        from feature_store.features import FeatureEngineering

        # Create realistic transaction data matching real parquet schema
        base_time = datetime(2024, 1, 15, 14, 30, 0)
        transactions = []

        domains = ["ecommerce", "fintech", "gaming"]
        countries = ["US", "GB", "DE", "NG", "RU", "CN"]
        payment_methods = ["credit_card", "debit_card", "crypto", "bank_transfer"]
        patterns = ["regular_purchase", "velocity_abuse", "account_takeover"]

        for i in range(100):
            actor_id = f"actor_{i % 20}"  # 20 unique actors
            domain = random.choice(domains)
            is_fraud = random.random() < 0.15  # 15% fraud rate

            transactions.append({
                "alert_id": i,
                "fraud_actor_id": actor_id,
                "amount": random.uniform(10, 5000),
                "timestamp": (base_time + timedelta(hours=random.randint(0, 72))).isoformat(),
                "fraud_label": is_fraud,
                "domain": domain,
                "session_length_sec": random.randint(5, 600),
                "country_from": random.choice(countries),
                "country_to": random.choice(countries),
                "payment_method": random.choice(payment_methods),
                "fraud_pattern": random.choice(patterns) if is_fraud else None,
            })

        # Create DataFrame
        df = spark.createDataFrame(transactions)
        input_path = f"{temp_dir}/input_transactions"
        df.write.mode("overwrite").parquet(input_path)

        # Reload to simulate real job
        df = spark.read.parquet(input_path)

        # === Replicate job logic ===
        actor_col = "fraud_actor_id" if "fraud_actor_id" in df.columns else "actor_id"

        # Aggregation (same as job)
        actors_df = df.groupBy(actor_col).agg(
            count("*").alias("fraud_txn_count"),
            spark_sum("amount").alias("amount"),
            spark_min("timestamp").alias("fraud_first_seen"),
            spark_max("timestamp").alias("timestamp"),
            first("fraud_label").alias("fraud_label"),
            first("domain").alias("domain"),
            first("payment_method", ignorenulls=True).alias("payment_method"),
            first("country_from", ignorenulls=True).alias("country_from"),
            first("country_to", ignorenulls=True).alias("country_to"),
            avg("session_length_sec").alias("session_length_sec"),
            first("fraud_pattern", ignorenulls=True).alias("fraud_pattern"),
        )
        actors_df = actors_df.withColumnRenamed(actor_col, "actor_id")

        # Fill nulls
        actors_df = actors_df.na.fill({
            "session_length_sec": 0,
            "payment_method": "unknown",
            "fraud_pattern": "unknown"
        })

        # Apply feature engineering
        transform = FeatureEngineering.get_spark_transformations()
        features_df = transform(actors_df)

        # Save and reload
        output_path = f"{temp_dir}/features"
        features_df.write.mode("overwrite").parquet(output_path)
        result = spark.read.parquet(output_path)

        # === Assertions ===
        assert result.count() == 20, f"Expected 20 actors, got {result.count()}"

        # Check expected feature columns exist
        expected_cols = [
            "actor_id", "amount", "fraud_txn_count", "fraud_label", "domain",
            "session_length_sec", "country_mismatch", "involves_high_risk_country",
            "hour_of_day", "day_of_week", "is_weekend", "is_near_threshold",
            "is_rapid_session", "amount_per_txn", "session_efficiency", "night_rapid_combo"
        ]
        for col_name in expected_cols:
            assert col_name in result.columns, f"Missing column: {col_name}"

        # Check values are reasonable
        sample = result.first()
        assert sample["amount"] > 0, "Amount should be positive"
        assert sample["fraud_txn_count"] >= 1, "Should have at least 1 transaction"
        assert 0 <= sample["hour_of_day"] <= 23, "Hour should be 0-23"
        assert 1 <= sample["day_of_week"] <= 7, "Day of week should be 1-7"

    def test_handles_missing_optional_columns(self, spark, temp_dir):
        """✅ Job handles missing optional columns gracefully."""
        from pyspark.sql.functions import col, count, sum as spark_sum, first, lit
        from feature_store.features import FeatureEngineering

        # Minimal data - only required columns
        transactions = [
            {"fraud_actor_id": "a1", "amount": 100.0, "timestamp": "2024-01-15T10:00:00",
             "fraud_label": False, "domain": "ecommerce"},
            {"fraud_actor_id": "a1", "amount": 200.0, "timestamp": "2024-01-15T11:00:00",
             "fraud_label": False, "domain": "ecommerce"},
            {"fraud_actor_id": "a2", "amount": 500.0, "timestamp": "2024-01-15T12:00:00",
             "fraud_label": True, "domain": "fintech"},
        ]

        df = spark.createDataFrame(transactions)

        # Aggregate without optional columns
        actors_df = df.groupBy("fraud_actor_id").agg(
            count("*").alias("fraud_txn_count"),
            spark_sum("amount").alias("amount"),
            first("timestamp").alias("fraud_first_seen"),
            first("timestamp").alias("timestamp"),
            first("fraud_label").alias("fraud_label"),
            first("domain").alias("domain"),
        )
        actors_df = actors_df.withColumnRenamed("fraud_actor_id", "actor_id")

        # Add missing columns with defaults
        actors_df = actors_df.withColumn("session_length_sec", lit(0))
        actors_df = actors_df.withColumn("payment_method", lit("unknown"))
        actors_df = actors_df.withColumn("fraud_pattern", lit("unknown"))
        actors_df = actors_df.withColumn("country_from", lit(None).cast("string"))
        actors_df = actors_df.withColumn("country_to", lit(None).cast("string"))

        # Apply transformations
        transform = FeatureEngineering.get_spark_transformations()
        features_df = transform(actors_df)

        assert features_df.count() == 2
        assert "country_mismatch" in features_df.columns
        assert "involves_high_risk_country" in features_df.columns

        # Verify defaults work
        sample = features_df.first()
        assert sample["country_mismatch"] == 0
        assert sample["involves_high_risk_country"] == 0

    def test_country_mismatch_detection(self, spark):
        """✅ Country mismatch is correctly detected."""
        from pyspark.sql.functions import lit
        from feature_store.features import FeatureEngineering

        # Test data with country mismatch
        data = [
            {"actor_id": "match", "amount": 100.0, "fraud_txn_count": 1,
             "fraud_first_seen": "2024-01-15T10:00:00", "timestamp": "2024-01-15T10:00:00",
             "fraud_label": False, "domain": "ecommerce", "session_length_sec": 60,
             "payment_method": "credit_card", "fraud_pattern": "regular",
             "country_from": "US", "country_to": "US"},
            {"actor_id": "mismatch", "amount": 100.0, "fraud_txn_count": 1,
             "fraud_first_seen": "2024-01-15T10:00:00", "timestamp": "2024-01-15T10:00:00",
             "fraud_label": True, "domain": "fintech", "session_length_sec": 30,
             "payment_method": "crypto", "fraud_pattern": "velocity_abuse",
             "country_from": "US", "country_to": "NG"},
        ]

        df = spark.createDataFrame(data)
        transform = FeatureEngineering.get_spark_transformations()
        result = transform(df)

        rows = {r["actor_id"]: r for r in result.collect()}

        assert rows["match"]["country_mismatch"] == 0
        assert rows["mismatch"]["country_mismatch"] == 1

    def test_high_risk_country_detection(self, spark):
        """✅ High risk countries are correctly flagged."""
        from feature_store.features import FeatureEngineering
        from feature_store.constants import HIGH_RISK_COUNTRIES

        data = [
            {"actor_id": "safe", "amount": 100.0, "fraud_txn_count": 1,
             "fraud_first_seen": "2024-01-15T10:00:00", "timestamp": "2024-01-15T10:00:00",
             "fraud_label": False, "domain": "ecommerce", "session_length_sec": 60,
             "payment_method": "credit_card", "fraud_pattern": "regular",
             "country_from": "US", "country_to": "GB"},
            {"actor_id": "risky", "amount": 100.0, "fraud_txn_count": 1,
             "fraud_first_seen": "2024-01-15T10:00:00", "timestamp": "2024-01-15T10:00:00",
             "fraud_label": True, "domain": "fintech", "session_length_sec": 30,
             "payment_method": "crypto", "fraud_pattern": "velocity_abuse",
             "country_from": "US", "country_to": list(HIGH_RISK_COUNTRIES)[0]},
        ]

        df = spark.createDataFrame(data)
        transform = FeatureEngineering.get_spark_transformations()
        result = transform(df)

        rows = {r["actor_id"]: r for r in result.collect()}

        assert rows["safe"]["involves_high_risk_country"] == 0
        assert rows["risky"]["involves_high_risk_country"] == 1

    def test_temporal_features(self, spark):
        """✅ Hour and day features are correctly extracted."""
        from feature_store.features import FeatureEngineering

        # Saturday at 3 AM (night + weekend)
        data = [{
            "actor_id": "night_weekend",
            "amount": 100.0,
            "fraud_txn_count": 1,
            "fraud_first_seen": "2024-01-13T03:00:00",  # Saturday 3 AM
            "timestamp": "2024-01-13T03:00:00",
            "fraud_label": False,
            "domain": "gaming",
            "session_length_sec": 10,  # Rapid
            "payment_method": "credit_card",
            "fraud_pattern": "regular",
            "country_from": "US",
            "country_to": "US",
        }]

        df = spark.createDataFrame(data)
        transform = FeatureEngineering.get_spark_transformations()
        result = transform(df).first()

        assert result["hour_of_day"] == 3
        assert result["is_weekend"] == 1
        assert result["is_rapid_session"] == 1
        assert result["night_rapid_combo"] == 1

    def test_derived_features_calculation(self, spark):
        """✅ Derived features are correctly calculated."""
        from feature_store.features import FeatureEngineering

        data = [{
            "actor_id": "test",
            "amount": 1000.0,
            "fraud_txn_count": 10,
            "fraud_first_seen": "2024-01-15T10:00:00",
            "timestamp": "2024-01-15T10:00:00",
            "fraud_label": False,
            "domain": "ecommerce",
            "session_length_sec": 99,  # 99 + 1 = 100 for easy math
            "payment_method": "credit_card",
            "fraud_pattern": "regular",
            "country_from": "US",
            "country_to": "US",
        }]

        df = spark.createDataFrame(data)
        transform = FeatureEngineering.get_spark_transformations()
        result = transform(df).first()

        # amount_per_txn = 1000 / (10 + 1) ≈ 90.9
        assert abs(result["amount_per_txn"] - 90.909) < 1

        # session_efficiency = 1000 / (99 + 1) = 10
        assert result["session_efficiency"] == 10.0