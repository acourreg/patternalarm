#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

# Load config
load_config

echo "🚀 Creating Kafka Topics..."
echo "Bootstrap Servers: ${KAFKA_BOOTSTRAP_SERVERS}"
echo ""

# Build common args
KAFKA_ARGS="--bootstrap-server ${KAFKA_BOOTSTRAP_SERVERS}"
if [[ -n "${KAFKA_CLIENT_CONFIG}" ]]; then
    KAFKA_ARGS="${KAFKA_ARGS} --command-config ${KAFKA_CLIENT_CONFIG}"
fi

# Create topics
create_topic() {
    local topic_name=$1
    echo "📊 Creating topic: ${topic_name}"
    
    kafka-topics.sh --create \
        ${KAFKA_ARGS} \
        --topic "${topic_name}" \
        --partitions "${KAFKA_PARTITIONS}" \
        --replication-factor "${KAFKA_REPLICATION_FACTOR}" \
        --if-not-exists
    
    echo "✅ Topic ${topic_name} ready"
    echo ""
}

create_topic "${TOPIC_GAMING}"
create_topic "${TOPIC_FINTECH}"
create_topic "${TOPIC_ECOMMERCE}"

# List all topics
echo "📋 Current topics:"
kafka-topics.sh --list ${KAFKA_ARGS}

echo ""
echo "✅ All topics created successfully!"