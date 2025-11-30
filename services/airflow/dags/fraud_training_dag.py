# services/airflow/dags/fraud_training_dag.py
"""
Fraud Model Training DAG
- Local: spark-submit --master local[*]
- Prod: EMR Serverless
"""

import os
from airflow import DAG
from airflow.operators.python import PythonOperator
from datetime import datetime, timedelta

ENV = os.getenv('ENVIRONMENT', 'local')
S3_BUCKET = os.getenv('S3_BUCKET', 'patternalarm-data-us-east-1')
EMR_APPLICATION_ID = os.getenv('EMR_APPLICATION_ID')
EMR_JOB_ROLE_ARN = os.getenv('EMR_JOB_ROLE_ARN')

default_args = {
    'owner': 'patternalarm',
    'start_date': datetime(2025, 1, 1),
    'retries': 1,
    'retry_delay': timedelta(minutes=5),
}

# ============================================================================
# GENERIC SPARK JOB FACTORY
# ============================================================================

def create_spark_task(dag, task_id: str, job_name: str, args: list):
    """Factory: returns PythonOperator (local) or EmrServerlessOperator (prod)."""

    if ENV == 'prod':
        from airflow.providers.amazon.aws.operators.emr import EmrServerlessStartJobOperator

        spark_args = [f's3://{S3_BUCKET}/spark-jobs/{job_name}'] + args

        return EmrServerlessStartJobOperator(
            task_id=task_id,
            application_id=EMR_APPLICATION_ID,
            execution_role_arn=EMR_JOB_ROLE_ARN,
            job_driver={
                'sparkSubmit': {
                    'entryPoint': f's3://{S3_BUCKET}/spark-jobs/{job_name}',
                    'entryPointArguments': args,
                    'sparkSubmitParameters': f'--py-files s3://{S3_BUCKET}/libs/feature_store.zip,s3://{S3_BUCKET}/libs/pyspark_libs.zip'
                }
            },
            configuration_overrides={
                'monitoringConfiguration': {
                    's3MonitoringConfiguration': {
                        'logUri': f's3://{S3_BUCKET}/logs/'
                    }
                }
            },
            wait_for_completion=True,
            dag=dag,
        )
    else:
        # Local mode
        def run_spark():
            import subprocess
            import sys

            os.environ['PYSPARK_PYTHON'] = sys.executable
            os.environ['PYSPARK_DRIVER_PYTHON'] = sys.executable

            cmd = [
                      'spark-submit', '--master', 'local[*]',
                      '--py-files', '/opt/airflow/feature-store/dist/feature_store.zip',
                      f'/opt/airflow/jobs/{job_name}'
                  ] + args

            print(f"🚀 {' '.join(cmd)}")
            result = subprocess.run(cmd, capture_output=True, text=True)
            print(result.stdout)
            if result.returncode != 0:
                print(result.stderr)
                raise Exception(f"{job_name} failed")

        return PythonOperator(
            task_id=task_id,
            python_callable=run_spark,
            dag=dag,
        )


def create_validate_task(dag):
    """Validate model metrics (works same for local and prod)."""

    def validate():
        import json

        if ENV == 'prod':
            import boto3
            s3 = boto3.client('s3')
            obj = s3.get_object(Bucket=S3_BUCKET, Key='models/model_metrics.json')
            metrics = json.loads(obj['Body'].read())
        else:
            with open('/opt/spark-data/models/model_metrics.json', 'r') as f:
                metrics = json.load(f)

        print(f"📊 {metrics}")
        assert metrics['accuracy'] > 0.80, "Accuracy too low"
        assert metrics.get('f1', metrics.get('auc', 0)) > 0.75, "F1/AUC too low"
        print("✅ Validation passed")

    return PythonOperator(
        task_id='validate_model',
        python_callable=validate,
        dag=dag,
    )


# ============================================================================
# DAG DEFINITION
# ============================================================================

with DAG(
        'fraud_model_training',
        default_args=default_args,
        schedule='0 2 * * *',
        catchup=False,
        tags=['ml', 'fraud']
) as dag:
    # Paths based on environment
    if ENV == 'prod':
        input_path = f's3://{S3_BUCKET}/data/processed/training_data.parquet'
        features_path = f's3://{S3_BUCKET}/data/features'
        model_output = f's3://{S3_BUCKET}/models/fraud_detector_v1'
    else:
        input_path = '/opt/spark-data/processed/training_data.parquet'
        features_path = '/opt/spark-data/features'
        model_output = '/opt/spark-data/models/fraud_detector_v1'

    # Tasks
    extract = create_spark_task(dag, 'extract_features', 'extract_features.py', [
        '--input-path', input_path,
        '--output-path', features_path
    ])

    train = create_spark_task(dag, 'train_model', 'train_model.py', [
        '--features-path', features_path,
        '--model-output', model_output,
        '--model-name', 'fraud-detector'
    ])

    validate = create_validate_task(dag)

    extract >> train >> validate
