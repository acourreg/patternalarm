# Platform Initialization Scripts

Run scripts in numbered order.

---

## 🐳 Local Development
```bash
# 1. Start Kafka
docker compose up

# 2. Create topics (uses localhost:9092 by default)
./1-kafka-topics.sh

# 3. Optional: Create PostgreSQL schema
psql -h localhost -U postgres -d patternalarm -f 2-rds-schema.sql
```

---

## ☁️ AWS Deployment

**Prerequisites:** Deploy infrastructure first
```bash
cd ../terraform
terraform apply
# Note the outputs: msk_bootstrap_brokers, rds_endpoint
```

**Setup:**
```bash
# 1. Configure
cp config.conf.example config.conf
# Edit config.conf with MSK broker endpoints from Terraform output

# 2. Create Kafka topics
./1-kafka-topics.sh

# 3. Create RDS schema
psql -h <rds-endpoint> -U dbadmin -d patternalarm -f 2-rds-schema.sql
```

---

## 📋 Requirements

**Local:** Docker, Docker Compose, Kafka CLI tools  
**AWS:** Terraform deployed, Kafka CLI tools, psql, network access to MSK/RDS