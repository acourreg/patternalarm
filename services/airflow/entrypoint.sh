#!/bin/bash
# services/airflow/entrypoint.sh

set -e

echo "🔧 Initializing Airflow..."

# Initialize database
airflow db migrate

# Create admin user
airflow users create \
    --username ${AIRFLOW_ADMIN_USERNAME:-admin} \
    --password ${AIRFLOW_ADMIN_PASSWORD:-admin} \
    --firstname Admin \
    --lastname User \
    --role Admin \
    --email admin@patternalarm.local 2>/dev/null || echo "User exists"

echo "🚀 Starting Airflow services..."

# Start scheduler in background, webserver in foreground
airflow scheduler &
exec airflow webserver --port 8080