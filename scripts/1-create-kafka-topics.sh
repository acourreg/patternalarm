#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$0")"
source "${SCRIPT_DIR}/common.sh"

# Load config
load_config

echo "🚀 Creating Kafka Topics..."
echo "Bootstrap Servers: ${KAFKA_BOOTSTRAP_SERVERS}"
echo ""

# Detect if using local Docker or remote MSK
if [[ "${KAFKA_BOOTSTRAP_SERVERS}" == "localhost:9092" ]]; then
    # Local Docker - use docker exec
    KAFKA_CMD="docker exec -it kafka kafka-topics"
    BOOTSTRAP_ARG="--bootstrap-server kafka:29092"
else
    # Remote MSK - use local kafka-topics.sh
    KAFKA_CMD="kafka-topics.sh"
    BOOTSTRAP_ARG="--bootstrap-server ${KAFKA_BOOTSTRAP_SERVERS}"
    
    # Add client config if specified
    if [[ -n "${KAFKA_CLIENT_CONFIG}" ]]; then
        BOOTSTRAP_ARG="${BOOTSTRAP_ARG} --command-config ${KAFKA_CLIENT_CONFIG}"
    fi
fi

# Create topics
create_topic() {
    local topic_name=$1
    echo "📊 Creating topic: ${topic_name}"
    
    ${KAFKA_CMD} --create \
        ${BOOTSTRAP_ARG} \
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
${KAFKA_CMD} --list ${BOOTSTRAP_ARG}

echo ""
echo "✅ All topics created successfully!"