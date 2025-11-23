# services/airflow/jobs/train_model.py

from pyspark.sql import SparkSession
from pyspark.ml.feature import VectorAssembler, StandardScaler, StringIndexer
from pyspark.ml.classification import RandomForestClassifier
from pyspark.ml import Pipeline
from pyspark.ml.evaluation import MulticlassClassificationEvaluator, BinaryClassificationEvaluator
import argparse
import sys
import json


def main(features_path, model_output):
    print("=" * 60)
    print("🤖 Step 2: Train Model")
    print("=" * 60)

    spark = SparkSession.builder \
        .appName("Fraud-TrainModel") \
        .config("spark.sql.shuffle.partitions", "4") \
        .getOrCreate()

    print(f"✅ Spark version: {spark.version}")
    print(f"📂 Loading features from: {features_path}")

    # Load features
    df = spark.read.parquet(features_path)

    print(f"✅ Loaded {df.count()} feature vectors")
    df.printSchema()

    # ✅ Prepare features (same as notebook)
    feature_cols = [
        'amount', 'transaction_count', 'amount_per_transaction',
        'time_delta_sec', 'velocity_per_sec'
    ]

    # String indexer for labels
    indexer = StringIndexer(
        inputCol="fraud_label",
        outputCol="label"
    )

    # Vector assembler
    assembler = VectorAssembler(
        inputCols=feature_cols,
        outputCol="features_raw"
    )

    # Standard scaler
    scaler = StandardScaler(
        inputCol="features_raw",
        outputCol="features",
        withStd=True,
        withMean=True
    )

    # Random Forest classifier
    rf = RandomForestClassifier(
        labelCol="label",
        featuresCol="features",
        numTrees=100,
        maxDepth=10,
        seed=42
    )

    # Pipeline
    pipeline = Pipeline(stages=[indexer, assembler, scaler, rf])

    # Train/test split
    train_df, test_df = df.randomSplit([0.8, 0.2], seed=42)

    print(f"📊 Train: {train_df.count()} | Test: {test_df.count()}")

    # Train
    print("🔧 Training model...")
    model = pipeline.fit(train_df)

    print("✅ Model trained!")

    # Evaluate
    predictions = model.transform(test_df)

    # Metrics
    evaluator_acc = MulticlassClassificationEvaluator(
        labelCol="label",
        predictionCol="prediction",
        metricName="accuracy"
    )

    evaluator_auc = BinaryClassificationEvaluator(
        labelCol="label",
        rawPredictionCol="rawPrediction",
        metricName="areaUnderROC"
    )

    accuracy = evaluator_acc.evaluate(predictions)
    auc = evaluator_auc.evaluate(predictions)

    print(f"📊 Accuracy: {accuracy:.4f}")
    print(f"📊 AUC: {auc:.4f}")

    # Save metrics
    metrics = {
        'accuracy': round(accuracy, 4),
        'auc': round(auc, 4),
        'train_samples': train_df.count(),
        'test_samples': test_df.count()
    }

    with open('/tmp/model_metrics.json', 'w') as f:
        json.dump(metrics, f, indent=2)

    # Save model
    model.write().overwrite().save(model_output)

    print(f"💾 Model saved to: {model_output}")
    print(f"📊 Metrics saved to: /tmp/model_metrics.json")
    print("🎉 Training complete!")

    spark.stop()
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('--features-path', required=True)
    parser.add_argument('--model-output', required=True)
    args = parser.parse_args()

    sys.exit(main(args.features_path, args.model_output))