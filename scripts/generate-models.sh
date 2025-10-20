#!/bin/bash
set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

# Paths
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SCHEMAS_DIR="$SCRIPT_DIR/schemas/avro"
FLINK_DIR="$PROJECT_ROOT/services/flink-processor"
GATEWAY_DIR="$PROJECT_ROOT/services/api-gateway"

echo -e "${BLUE}╔═══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  PatternAlarm - Model Generation from Avro Schemas   ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════╝${NC}"
echo ""

SCHEMA_COUNT=$(ls -1 "$SCHEMAS_DIR"/*.avsc 2>/dev/null | wc -l)
echo -e "${GREEN}📂 Found $SCHEMA_COUNT schema(s)${NC}"
ls -1 "$SCHEMAS_DIR"/*.avsc | xargs -n1 basename
echo ""

# =============================================================================
# SCALA Generation
# =============================================================================

generate_scala() {
    echo -e "${YELLOW}🔧 SCALA: Generating via sbt-avrohugger${NC}"
    
    cd "$FLINK_DIR"
    
    # Génère les models (ignore les erreurs de compilation du code utilisateur)
    sbt avroScalaGenerate > /tmp/sbt-avro.log 2>&1
    
    # Vérifie si les models sont générés
    MODEL_DIR="target/scala-2.12/src_managed/main/compiled_avro/com/patternalarm/flinkprocessor/model"
    if [ -d "$MODEL_DIR" ] && [ "$(ls -A $MODEL_DIR/*.scala 2>/dev/null | wc -l)" -gt 0 ]; then
        echo -e "${GREEN}✅ Avro models generated${NC}"
        
        # Copie vers src/main/scala (le build.sbt le fait automatiquement à la compile)
        # Mais on force une compile partielle pour déclencher la copie
        sbt "set Compile / compileOrder := CompileOrder.Mixed" compile > /tmp/sbt-copy.log 2>&1 || true
        
        # Format
        sbt scalafmt > /dev/null 2>&1 && echo -e "${GREEN}✨ Formatted with scalafmt${NC}"
        
        echo -e "${YELLOW}⚠️  Note: Fix compilation errors in your Flink code${NC}"
    else
        echo -e "${RED}❌ Model generation failed${NC}"
        cat /tmp/sbt-avro.log
        return 1
    fi
    
    cd "$PROJECT_ROOT"
}

# =============================================================================
# PYTHON Generation
# =============================================================================

generate_python() {
    echo -e "${YELLOW}🐍 PYTHON: Generating Pydantic models${NC}"
    
    OUTPUT_DIR="$GATEWAY_DIR/src/models"
    mkdir -p "$OUTPUT_DIR"
    
    for schema in "$SCHEMAS_DIR"/*.avsc; do
        schema_name=$(basename "$schema" .avsc)
        output_file="$OUTPUT_DIR/$(echo $schema_name | tr '[:upper:]' '[:lower:]').py"
        
        python3 << PYTHON_CODE
import json

with open("$schema", "r") as f:
    avro = json.load(f)

def type_convert(t):
    if isinstance(t, str):
        return {"string": "str", "int": "int", "long": "int", "float": "float", 
                "double": "float", "boolean": "bool"}.get(t, "str")
    elif isinstance(t, dict):
        if t.get("type") == "array":
            return f"List[{type_convert(t['items'])}]"
        elif t.get("logicalType") == "timestamp-millis":
            return "datetime"
    elif isinstance(t, list):
        non_null = [x for x in t if x != "null"]
        if non_null:
            return f"Optional[{type_convert(non_null[0])}]"
    return "str"

lines = [
    "from pydantic import BaseModel",
    "from typing import Optional, List",
    "from datetime import datetime",
    "",
    f"class {avro['name']}(BaseModel):"
]

if "doc" in avro:
    lines.append(f'    """{avro["doc"]}"""')

for field in avro["fields"]:
    field_type = type_convert(field["type"])
    default = " = None" if "Optional" in field_type else ""
    lines.append(f"    {field['name']}: {field_type}{default}")

with open("$output_file", "w") as f:
    f.write("\n".join(lines) + "\n")
PYTHON_CODE
    done
    
    # __init__.py
    cat > "$OUTPUT_DIR/__init__.py" << 'EOF'
"""Auto-generated from Avro schemas"""
from .alert import Alert
from .alertmetadata import AlertMetadata
from .transactionevent import TransactionEvent
__all__ = ["Alert", "AlertMetadata", "TransactionEvent"]
EOF
    
    echo -e "${GREEN}✅ Python models generated${NC}"
}

# =============================================================================
# Main
# =============================================================================

case "${1:-}" in
    --scala-only)
        generate_scala
        ;;
    --python-only)
        generate_python || exit 1
        ;;
    --help|-h)
        echo "Usage: ./generate-models.sh [--scala-only|--python-only]"
        exit 0
        ;;
    *)
        generate_scala
        generate_python || exit 1
        ;;
esac

echo ""
echo -e "${GREEN}✅ Model generation complete!${NC}"
echo -e "${YELLOW}💡 Next: Fix compilation errors in Flink code${NC}"