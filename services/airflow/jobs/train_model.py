# services/airflow/jobs/train_model.py
"""
Fraud Model Training - Multi-class Classification
Faithful to POC notebook: 10 classes, 15 features, RF(150 trees, depth 16)
"""

from pyspark.sql import SparkSession
from pyspark.ml.feature import VectorAssembler, StringIndexer
from pyspark.ml.classification import RandomForestClassifier
from pyspark.ml.evaluation import MulticlassClassificationEvaluator
from pyspark.ml import Pipeline
import argparse
import sys
import json
import os

# 15 features from POC (payment_idx and domain_idx created by pipeline)
FEATURE_COLS = [
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
    "payment_idx",
    "domain_idx"
]


def build_pipeline():
    """Build ML pipeline matching POC notebook."""
    return Pipeline(stages=[
        # Categorical indexers
        StringIndexer(inputCol="payment_method", outputCol="payment_idx", handleInvalid="keep"),
        StringIndexer(inputCol="domain", outputCol="domain_idx", handleInvalid="keep"),
        StringIndexer(inputCol="fraud_pattern_simplified", outputCol="label_idx", handleInvalid="keep"),

        # Feature assembly
        VectorAssembler(inputCols=FEATURE_COLS, outputCol="features", handleInvalid="keep"),

        # RandomForest - exact params from POC
        RandomForestClassifier(
            featuresCol="features",
            labelCol="label_idx",
            numTrees=150,
            maxDepth=16,
            minInstancesPerNode=3,
            maxBins=64,
            seed=42
        )
    ])


def evaluate(model, test_df):
    """Evaluate model - multi-class metrics."""
    predictions = model.transform(test_df)

    accuracy = MulticlassClassificationEvaluator(
        labelCol="label_idx", predictionCol="prediction", metricName="accuracy"
    ).evaluate(predictions)

    f1 = MulticlassClassificationEvaluator(
        labelCol="label_idx", predictionCol="prediction", metricName="f1"
    ).evaluate(predictions)

    return {"accuracy": round(accuracy, 4), "f1": round(f1, 4)}


def save_metrics(metrics, model_output):
    """Save metrics JSON next to model."""
    metrics_path = os.path.dirname(model_output) + "/model_metrics.json"
    os.makedirs(os.path.dirname(metrics_path), exist_ok=True)
    with open(metrics_path, 'w') as f:
        json.dump(metrics, f, indent=2)
    print(f"📄 Metrics saved: {metrics_path}")


def main(features_path: str, model_output: str, model_name: str = "fraud-detector"):
    print("=" * 60)
    print("🎯 Train Model (Multi-class, 10 classes)")
    print("=" * 60)

    spark = SparkSession.builder.appName("Fraud-TrainModel").getOrCreate()

    # Load features
    print(f"📂 Loading: {features_path}")
    df = spark.read.parquet(features_path)
    print(f"✅ {df.count():,} samples")

    # Show class distribution
    print("\n📊 Class distribution:")
    df.groupBy("fraud_pattern_simplified").count().orderBy("count", ascending=False).show(10)

    # Split 80/20
    train_df, test_df = df.randomSplit([0.8, 0.2], seed=42)
    print(f"📊 Train: {train_df.count():,}, Test: {test_df.count():,}")

    # Train
    print("\n🚀 Training RandomForest (150 trees, depth 16)...")
    pipeline = build_pipeline()
    model = pipeline.fit(train_df)
    print("✅ Training complete")

    # Evaluate
    metrics = evaluate(model, test_df)
    print(f"\n📊 Accuracy: {metrics['accuracy']:.2%}")
    print(f"📊 F1 Score: {metrics['f1']:.2%}")

    # Save model
    print(f"\n💾 Saving: {model_output}")
    model.write().overwrite().save(model_output)
    save_metrics(metrics, model_output)

    print(f"✅ Model registered as: {model_name}")
    spark.stop()
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('--features-path', required=True)
    parser.add_argument('--model-output', required=True)
    parser.add_argument('--model-name', default='fraud-detector')
    args = parser.parse_args()

    sys.exit(main(args.features_path, args.model_output, args.model_name))