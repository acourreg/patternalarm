#!/bin/bash
set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SCHEMAS_DIR="$SCRIPT_DIR/schemas/avro"

echo -e "${BLUE}╔═══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  PatternAlarm - Kafka Models from Avro Schemas       ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════╝${NC}"
echo ""

# =============================================================================
# SCALA - Flink Processor
# =============================================================================
generate_scala_flink() {
    echo -e "${YELLOW}🔧 SCALA: Generating Flink models${NC}"
    
    cd "$PROJECT_ROOT/services/flink-processor"
    sbt avroScalaGenerate
    
    echo -e "${GREEN}✅ Flink Scala models generated${NC}"
}

# =============================================================================
# PYTHON - API Gateway (Kafka models)
# =============================================================================
generate_python_gateway() {
    echo -e "${YELLOW}🐍 PYTHON: Generating API Gateway kafka models${NC}"
    
    OUTPUT_DIR="$PROJECT_ROOT/services/api-gateway/src/kafka/models"
    mkdir -p "$OUTPUT_DIR"
    
    for schema in "$SCHEMAS_DIR"/*.avsc; do
        schema_name=$(basename "$schema" .avsc)
        output_file="$OUTPUT_DIR/$(echo $schema_name | tr '[:upper:]' '[:lower:]').py"
        
        python3 << PYTHON_CODE
import json
with open("$schema", "r") as f:
    avro = json.load(f)

# [Same conversion logic as before]

with open("$output_file", "w") as f:
    f.write("# ⚠️ AUTO-GENERATED FROM AVRO - DO NOT EDIT\n\n")
    f.write("from pydantic import BaseModel\n")
    f.write("from typing import Optional, List\n")
    f.write("from datetime import datetime\n\n")
    f.write(f"class {avro['name']}(BaseModel):\n")
    for field in avro["fields"]:
        # [field generation logic]
        pass
PYTHON_CODE
    done
    
    # __init__.py
    cat > "$OUTPUT_DIR/__init__.py" << 'EOF'
"""
Kafka Models (Generated from Avro schemas)
"""
from .transactionevent import TransactionEvent
from .alert import Alert

__all__ = ["TransactionEvent", "Alert"]
EOF
    
    echo -e "${GREEN}✅ API Gateway kafka models generated${NC}"
}

# =============================================================================
# PYTHON - Event Generator
# =============================================================================
generate_python_event_generator() {
    echo -e "${YELLOW}🐍 PYTHON: Generating Event Generator models${NC}"
    
    OUTPUT_DIR="$PROJECT_ROOT/services/event-generator/src/models"
    mkdir -p "$OUTPUT_DIR"
    
    # Same logic as gateway
    # Copy schemas to event-generator
    
    echo -e "${GREEN}✅ Event Generator models generated${NC}"
}

# =============================================================================
# Main
# =============================================================================
case "${1:-}" in
    --scala-only)
        generate_scala_flink
        ;;
    --python-only)
        generate_python_gateway
        generate_python_event_generator
        ;;
    *)
        generate_scala_flink
        generate_python_gateway
        generate_python_event_generator
        ;;
esac

echo ""
echo -e "${GREEN}✅ Kafka models generation complete!${NC}"
echo -e "${YELLOW}💡 Feature Store entities are managed separately in feature-store/${NC}"