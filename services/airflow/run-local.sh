#!/bin/bash
# services/airflow/run-local.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "🔨 Building Airflow image..."
docker build -t patternalarm-airflow:latest "$SCRIPT_DIR"

echo "🧹 Cleaning up old container..."
docker stop airflow 2>/dev/null && docker rm airflow 2>/dev/null || true

echo "🚀 Starting Airflow..."
docker run -d \
  --name airflow \
  --network scripts_patternalarm \
  -p 8090:8080 \
  -v "$PROJECT_ROOT/data:/opt/spark-data" \
  patternalarm-airflow:latest

echo "⏳ Waiting for Airflow..."
sleep 20

echo "✅ Airflow ready: http://localhost:8090 (admin/admin)"
docker logs airflow --tail 10