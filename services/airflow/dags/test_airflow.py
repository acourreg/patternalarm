"""
Ultra minimal test DAG
"""
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.bash import BashOperator
from datetime import datetime, timedelta

# Default args
default_args = {
    'owner': 'patternalarm',
    'depends_on_past': False,
    'start_date': datetime(2025, 1, 1),
    'email_on_failure': False,
    'email_on_retry': False,
    'retries': 1,
    'retry_delay': timedelta(minutes=5),
}

# Create DAG
dag = DAG(
    'test_dag',
    default_args=default_args,
    description='Ultra minimal test DAG',
    schedule=None,  # Manual trigger only
    catchup=False,
    tags=['test'],
)

# Task 1: Python function
def say_hello():
    print("✅ Hello from Airflow!")
    print("🚀 PatternAlarm ML Pipeline")
    return "Success"

hello_task = PythonOperator(
    task_id='say_hello',
    python_callable=say_hello,
    dag=dag,
)

# Task 2: Bash command
bash_task = BashOperator(
    task_id='print_date',
    bash_command='date',
    dag=dag,
)

# Task 3: Check feature store
def check_feature_store():
    import sys
    print(f"Python path: {sys.path}")
    print("✅ Feature store check done")
    return "OK"

check_task = PythonOperator(
    task_id='check_feature_store',
    python_callable=check_feature_store,
    dag=dag,
)

# Define task order
hello_task >> bash_task >> check_task