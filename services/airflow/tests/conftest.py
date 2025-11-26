# services/airflow/tests/conftest.py


import pytest
import tempfile
import shutil
from pyspark.sql import SparkSession
from datetime import datetime, timedelta
import random


@pytest.fixture(scope="session")
def spark():
    """Local Spark session for testing (no cluster needed)."""
    spark = SparkSession.builder \
        .master("local[2]") \
        .appName("PatternAlarm-Tests") \
        .config("spark.sql.shuffle.partitions", "2") \
        .config("spark.ui.enabled", "false") \
        .getOrCreate()

    yield spark
    spark.stop()


@pytest.fixture
def temp_dir():
    """Temporary directory for test outputs."""
    path = tempfile.mkdtemp()
    yield path
    shutil.rmtree(path)


@pytest.fixture
def sample_transactions(spark, temp_dir):
    """Create sample transaction data as parquet."""
    base_time = datetime(2025, 1, 1, 12, 0, 0)

    data = []
    for i in range(100):
        actor_id = f"actor_{i % 10}"  # 10 unique actors
        is_fraud = i % 10 == 0  # 10% fraud rate

        data.append({
            "transaction_id": f"txn_{i}",
            "actor_id": actor_id,
            "amount": round(random.uniform(10, 1000), 2),
            "timestamp": base_time + timedelta(minutes=i),
            "country": random.choice(["US", "UK", "NG", "RU"]),
            "fraud_label": "fraud" if is_fraud else "legitimate"
        })

    df = spark.createDataFrame(data)

    output_path = f"{temp_dir}/transactions"
    df.write.parquet(output_path)

    return output_path


@pytest.fixture
def sample_features(spark, temp_dir):
    """Create sample features data as parquet."""
    data = []
    for i in range(50):
        is_fraud = i % 5 == 0

        data.append({
            "actor_id": f"actor_{i}",
            "amount": round(random.uniform(100, 5000), 2),
            "transaction_count": random.randint(1, 20),
            "amount_per_transaction": round(random.uniform(50, 500), 2),
            "time_delta_sec": random.randint(60, 3600),
            "velocity_per_sec": round(random.uniform(0.1, 10), 4),
            "fraud_label": "fraud" if is_fraud else "legitimate"
        })

    df = spark.createDataFrame(data)

    output_path = f"{temp_dir}/features"
    df.write.parquet(output_path)

    return output_path