# services/airflow/tests/conftest.py

import pytest
import os
import sys
import tempfile
import shutil

# 🔧 Fix Python version mismatch
os.environ['PYSPARK_PYTHON'] = sys.executable
os.environ['PYSPARK_DRIVER_PYTHON'] = sys.executable


@pytest.fixture(scope="session")
def spark():
    """Create SparkSession for tests."""
    from pyspark.sql import SparkSession

    spark = SparkSession.builder \
        .appName("test") \
        .master("local[*]") \
        .config("spark.driver.memory", "1g") \
        .config("spark.sql.shuffle.partitions", "2") \
        .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")
    yield spark
    spark.stop()


@pytest.fixture(scope="function")
def temp_dir():
    """Temporary directory for test outputs."""
    path = tempfile.mkdtemp()
    yield path
    shutil.rmtree(path, ignore_errors=True)


@pytest.fixture(scope="session")
def sample_features(spark, tmp_path_factory):
    """Generate sample feature data."""
    import random

    tmp_path = tmp_path_factory.mktemp("data")
    features_path = str(tmp_path / "features")

    data = []
    for i in range(50):
        is_fraud = i < 10
        data.append({
            "actor_id": f"actor_{i % 10}",
            "amount": random.uniform(1000, 10000) if is_fraud else random.uniform(10, 500),
            "transaction_count": random.randint(1, 5),
            "amount_per_transaction": random.uniform(100, 2000),
            "time_delta_sec": random.uniform(1, 100),
            "velocity_per_sec": random.uniform(0.1, 10),
            "fraud_label": "fraud" if is_fraud else "legitimate"
        })

    df = spark.createDataFrame(data)
    df.write.parquet(features_path)

    return features_path


@pytest.fixture(scope="session")
def sample_transactions(spark, tmp_path_factory):
    """Generate sample transaction data."""
    from datetime import datetime, timedelta
    import random

    tmp_path = tmp_path_factory.mktemp("data")
    transactions_path = str(tmp_path / "transactions")

    data = []
    base_time = datetime(2024, 1, 1, 12, 0, 0)

    for i in range(100):
        is_fraud = i < 20
        data.append({
            "transaction_id": f"tx_{i}",
            "actor_id": f"actor_{i % 10}",
            "amount": random.uniform(1000, 10000) if is_fraud else random.uniform(10, 500),
            "timestamp": (base_time + timedelta(minutes=i)).isoformat(),
            "fraud_label": "fraud" if is_fraud else "legitimate"
        })

    df = spark.createDataFrame(data)
    df.write.parquet(transactions_path)

    return transactions_path