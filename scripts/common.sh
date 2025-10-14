#!/bin/bash

# Load configuration
load_config() {
    local config_file="${SCRIPT_DIR}/config.conf"
    
    if [[ ! -f "${config_file}" ]]; then
        echo "❌ Error: config.conf not found"
        echo "Run: cp config.conf.example config.conf"
        exit 1
    fi
    
    source "${config_file}"
    
    # Validate required vars
    if [[ -z "${KAFKA_BOOTSTRAP_SERVERS}" ]]; then
        echo "❌ Error: KAFKA_BOOTSTRAP_SERVERS not set in config.conf"
        exit 1
    fi
}
```

---

### **scripts/.gitignore**
```
config.conf