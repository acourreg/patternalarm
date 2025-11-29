# services/airflow/jobs/extract_features.py

from pyspark.sql import SparkSession
from pyspark.sql.functions import (
    col, count, sum as spark_sum, min as spark_min, max as spark_max,
    first, avg, lit
)
import argparse
import sys

from feature_store.features import FeatureEngineering


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

    df = spark.read.parquet(input_path)
    print(f"✅ Loaded {df.count()} transactions")
    df.printSchema()

    # Detect actor column
    actor_col = "fraud_actor_id" if "fraud_actor_id" in df.columns else "actor_id"
    print(f"📌 Using actor column: {actor_col}")

    # Detect session column
    session_col = "session_length_sec"
    if "session_length_sec" not in df.columns:
        if "session_duration_sec" in df.columns:
            session_col = "session_duration_sec"
        else:
            df = df.withColumn("session_length_sec", lit(0))

    # Aggregate by actor - prepare columns needed by get_spark_transformations()
    agg_exprs = [
        count("*").alias("fraud_txn_count"),
        spark_sum("amount").alias("amount"),
        spark_min("timestamp").alias("fraud_first_seen"),
        spark_max("timestamp").alias("timestamp"),
        first("fraud_label").alias("fraud_label"),
        first("domain").alias("domain"),
        first("payment_method", ignorenulls=True).alias("payment_method"),
        first("country_from", ignorenulls=True).alias("country_from"),
        first("country_to", ignorenulls=True).alias("country_to"),
        avg(session_col).alias("session_length_sec"),
    ]

    # Add fraud_pattern if available
    if "fraud_pattern" in df.columns:
        agg_exprs.append(first("fraud_pattern", ignorenulls=True).alias("fraud_pattern"))
    elif "pattern" in df.columns:
        agg_exprs.append(first("pattern", ignorenulls=True).alias("fraud_pattern"))

    actors_df = df.groupBy(actor_col).agg(*agg_exprs)
    actors_df = actors_df.withColumnRenamed(actor_col, "actor_id")

    print(f"✅ Aggregated {actors_df.count()} unique actors")

    # Fill nulls for required columns
    actors_df = actors_df.na.fill({
        "session_length_sec": 0,
        "payment_method": "unknown",
    })

    # Add fraud_pattern if missing
    if "fraud_pattern" not in actors_df.columns:
        actors_df = actors_df.withColumn("fraud_pattern", lit("unknown"))
    else:
        actors_df = actors_df.na.fill({"fraud_pattern": "unknown"})

    print("📊 Aggregated schema:")
    actors_df.printSchema()

    # Apply Spark transformations from feature store
    transform = FeatureEngineering.get_spark_transformations()
    features_df = transform(actors_df)

    print(f"✅ Extracted features for {features_df.count()} actors")
    print("📊 Features schema:")
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