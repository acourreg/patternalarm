# services/airflow/jobs/extract_features.py

from pyspark.sql import SparkSession
from pyspark.sql.functions import col, count, sum as spark_sum, min as spark_min, max as spark_max
import argparse
import sys

# ✅ Import feature store
from feature_store.features import FeatureEngineering
from feature_store.entities import ActorTransactions, Transaction


def main(input_path, output_path):
    print("=" * 60)
    print("📥 Step 1: Extract Features")
    print("=" * 60)

    spark = SparkSession.builder \
        .appName("Fraud-ExtractFeatures") \
        .config("spark.sql.shuffle.partitions", "4") \
        .getOrCreate()

    print(f"✅ Spark version: {spark.version}")
    print(f"📂 Loading from: {input_path}")

    # Load parquet
    df = spark.read.parquet(input_path)

    print(f"✅ Loaded {df.count()} transactions")
    df.printSchema()

    # Group by actor (like in notebook)
    actors_df = df.groupBy("actor_id").agg(
        count("*").alias("transaction_count"),
        spark_sum("amount").alias("total_amount"),
        spark_min("timestamp").alias("first_transaction"),
        spark_max("timestamp").alias("last_transaction"),
        col("fraud_label").alias("fraud_label")  # Assuming fraud_label exists
    )

    print(f"✅ Aggregated {actors_df.count()} unique actors")

    # ✅ Convert to pandas for feature engineering
    actors_pd = actors_df.toPandas()

    # ✅ Use feature store to extract features
    features_list = []

    for _, row in actors_pd.iterrows():
        # Create ActorTransactions entity
        actor = ActorTransactions(
            actor_id=row['actor_id'],
            transactions=[],  # Simplified, ideally load full txns
            fraud_label=row['fraud_label']
        )

        # Extract features using feature store
        features = FeatureEngineering.extract_features_pandas(actor)
        features_list.append(features)

    # Convert back to Spark DataFrame
    features_df = spark.createDataFrame(features_list)

    print(f"✅ Extracted {len(features_list)} feature vectors")
    features_df.printSchema()

    # Save
    features_df.write.mode("overwrite").parquet(output_path)

    print(f"💾 Features saved to: {output_path}")
    print("🎉 Feature extraction complete!")

    spark.stop()
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('--input-path', required=True)
    parser.add_argument('--output-path', required=True)
    args = parser.parse_args()

    sys.exit(main(args.input_path, args.output_path))