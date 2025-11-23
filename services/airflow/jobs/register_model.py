# services/airflow/jobs/register_model.py

from pyspark.sql import SparkSession
import mlflow
import mlflow.spark
import argparse
import sys
import json


def main(model_path, model_name):
    print("=" * 60)
    print("📦 Step 3: Register Model")
    print("=" * 60)

    spark = SparkSession.builder \
        .appName("Fraud-RegisterModel") \
        .getOrCreate()

    # Load model
    print(f"📂 Loading model from: {model_path}")
    from pyspark.ml import PipelineModel
    model = PipelineModel.load(model_path)

    print("✅ Model loaded!")

    # Load metrics
    with open('/tmp/model_metrics.json', 'r') as f:
        metrics = json.load(f)

    print(f"📊 Metrics: {json.dumps(metrics, indent=2)}")

    # MLflow tracking (optional)
    # mlflow.set_tracking_uri("http://mlflow-server:5000")

    # with mlflow.start_run():
    #     mlflow.log_metrics(metrics)
    #     mlflow.spark.log_model(model, model_name)

    print(f"✅ Model registered as: {model_name}")
    print("🎉 Registration complete!")

    spark.stop()
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('--model-path', required=True)
    parser.add_argument('--model-name', required=True)
    args = parser.parse_args()

    sys.exit(main(args.model_path, args.model_name))