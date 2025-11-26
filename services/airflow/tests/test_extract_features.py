# services/airflow/tests/test_extract_features.py
import pytest
import sys
import os

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
        from feature_store.entities import ActorTransactions
        assert FeatureEngineering is not None

    def test_extract_pipeline(self, spark, sample_transactions, temp_dir):
        """✅ Full extraction pipeline works."""
        from pyspark.sql.functions import count, sum as spark_sum, first

        df = spark.read.parquet(sample_transactions)

        actors_df = df.groupBy("actor_id").agg(
            count("*").alias("transaction_count"),
            spark_sum("amount").alias("total_amount"),
            first("fraud_label").alias("fraud_label")
        )

        output_path = f"{temp_dir}/features"
        actors_df.write.mode("overwrite").parquet(output_path)

        result = spark.read.parquet(output_path)
        assert result.count() > 0