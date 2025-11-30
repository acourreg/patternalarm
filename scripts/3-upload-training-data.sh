#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$0")"
source "${SCRIPT_DIR}/common.sh"

load_config

S3_BUCKET="${S3_BUCKET:-patternalarm-data-us-east-1}"
LOCAL_PATH="$(git rev-parse --show-toplevel)/data/processed/training_data.parquet"

echo "📤 Uploading training data to S3..."
echo "Local: ${LOCAL_PATH}"
echo "S3: s3://${S3_BUCKET}/data/processed/training_data.parquet/"
echo ""

if [[ ! -d "${LOCAL_PATH}" ]]; then
    echo "❌ Folder not found: ${LOCAL_PATH}"
    exit 1
fi

aws s3 cp "${LOCAL_PATH}" "s3://${S3_BUCKET}/data/processed/training_data.parquet/" --recursive

echo "✅ Training data uploaded successfully!"
echo ""
echo "Verify:"
echo "aws s3 ls s3://${S3_BUCKET}/data/processed/training_data.parquet/"