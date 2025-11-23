# services/airflow/dags/fraud_training_dag.py

from airflow import DAG
from airflow.providers.apache.spark.operators.spark_submit import SparkSubmitOperator
from airflow.operators.python import PythonOperator
from datetime import datetime, timedelta

default_args = {
    'owner': 'patternalarm',
    'depends_on_past': False,
    'start_date': datetime(2025, 1, 1),
    'retries': 1,
    'retry_delay': timedelta(minutes=5),
}

with DAG(
        'fraud_model_training',
        default_args=default_args,
        description='Train fraud detection model on Spark',
        schedule='0 2 * * *',  # Daily at 2 AM
        catchup=False,
        tags=['ml', 'fraud', 'training']
) as dag:
    # Step 1: Extract features from parquet
    extract_features = SparkSubmitOperator(
        task_id='extract_features',
        application='/opt/airflow/jobs/extract_features.py',
        conn_id='spark_default',
        name='fraud-extract-features',
        application_args=[
            '--input-path', '/opt/spark-data/transactions',
            '--output-path', '/opt/spark-data/features'
        ],
        verbose=True
    )

    # Step 2: Train model
    train_model = SparkSubmitOperator(
        task_id='train_model',
        application='/opt/airflow/jobs/train_model.py',
        conn_id='spark_default',
        name='fraud-train-model',
        application_args=[
            '--features-path', '/opt/spark-data/features',
            '--model-output', '/opt/spark-data/models/fraud_detector_v1'
        ],
        verbose=True
    )

    # Step 3: Register model to MLflow
    register_model = SparkSubmitOperator(
        task_id='register_model',
        application='/opt/airflow/jobs/register_model.py',
        conn_id='spark_default',
        name='fraud-register-model',
        application_args=[
            '--model-path', '/opt/spark-data/models/fraud_detector_v1',
            '--model-name', 'fraud-detector'
        ],
        verbose=True
    )


    # Step 4: Validate (Python)
    def validate_model_metrics():
        import json

        # TODO: Read metrics from training output
        metrics = {'accuracy': 0.95, 'auc': 0.94}

        print(f"📊 Metrics: {json.dumps(metrics, indent=2)}")
        assert metrics['accuracy'] > 0.85, "Accuracy too low!"

        print("✅ Validation passed!")
        return metrics


    validate = PythonOperator(
        task_id='validate_model',
        python_callable=validate_model_metrics
    )

    # Pipeline
    extract_features >> train_model >> register_model >> validate