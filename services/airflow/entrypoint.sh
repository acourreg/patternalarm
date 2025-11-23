#!/bin/bash
set -e

echo "🔧 Initializing Airflow..."

# Initialize database
airflow db init

# Create admin user
airflow users create \
    --username ${AIRFLOW_ADMIN_USERNAME:-admin} \
    --password ${AIRFLOW_ADMIN_PASSWORD:-admin} \
    --firstname Admin \
    --lastname User \
    --role Admin \
    --email admin@admin.com 2>/dev/null || echo "User exists"

# ✅ Create Spark connection
airflow connections delete spark_default 2>/dev/null || true
airflow connections add spark_default \
    --conn-type spark \
    --conn-host spark-master \
    --conn-port 7077 \
    --conn-extra '{"queue": "default", "deploy-mode": "client"}'

echo "🚀 Starting Airflow services..."

# Start webserver and scheduler
airflow webserver --port 8080 &
airflow scheduler