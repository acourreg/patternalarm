# PatternAlarm API Gateway

Mock FastAPI service providing:
- **Model Serving** for Flink (`POST /predict`)
- **Query Layer** for Dashboard (`GET /alerts`, `/analytics`)

---

## 🚀 Quick Start

### 1. Install Dependencies
```bash
# Create virtual environment
python3 -m venv venv

# Activate virtual environment
source venv/bin/activate  # Mac/Linux
# OR: venv\Scripts\activate  # Windows

# Install requirements
pip install -r requirements.txt
```

### 2. Run Server
```bash
python main.py
```

Server starts on: `http://localhost:8000`

---

## 📡 API Endpoints

### 🏥 Health Check

**Request:**
```bash
curl http://localhost:8000/health
```

**Expected Response:**
```json
{
  "status": "healthy",
  "database": "connected",
  "redis": "connected",
  "model_loaded": true,
  "model_version": "v1.0-mocked",
  "timestamp": "2025-10-16T18:11:55.767675"
}
```

---

### 🤖 ML Model Prediction (Flink calls this)

**Request:**
```bash
curl -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d '{
    "actor_id": "A123456",
    "domain": "gaming",
    "transaction_count": 5,
    "total_amount": 750.0,
    "time_delta_sec": 120,
    "window_start": "2025-10-16T18:00:00",
    "window_end": "2025-10-16T18:02:00",
    "transactions": [
      {
        "transaction_id": "TXN001",
        "timestamp": "2025-10-16T18:00:15",
        "amount": 150.0,
        "ip_address": "45.142.23.45",
        "device_id": "DEV123"
      },
      {
        "transaction_id": "TXN002",
        "timestamp": "2025-10-16T18:00:45",
        "amount": 200.0,
        "ip_address": "192.168.1.1",
        "device_id": "DEV123"
      },
      {
        "transaction_id": "TXN003",
        "timestamp": "2025-10-16T18:01:20",
        "amount": 100.0,
        "ip_address": "45.142.67.89",
        "device_id": "DEV456"
      }
    ]
  }'
```

**Expected Response:**
```json
{
  "fraud_score": 89,
  "model_version": "v1.0-mocked",
  "inference_time_ms": 17,
  "transactions_analyzed": 3
}
```

**Note:** `fraud_score` varies (random mock logic)

---

### 🚨 Get Alerts List

**Request:**
```bash
curl http://localhost:8000/alerts
```

**Expected Response:**
```json
{
  "alerts": [
    {
      "alert_id": 1,
      "alert_type": "velocity_spike",
      "domain": "gaming",
      "actor_id": "A456789",
      "severity": "HIGH",
      "fraud_score": 85,
      "transaction_count": 5,
      "total_amount": 750.0,
      "first_seen": "2025-10-16T18:28:00",
      "last_seen": "2025-10-16T18:32:00"
    }
  ],
  "total": 10,
  "page": 1
}
```

**With Filters:**
```bash
# Filter by domain
curl "http://localhost:8000/alerts?domain=gaming"

# Filter by severity
curl "http://localhost:8000/alerts?severity=CRITICAL"

# Limit results
curl "http://localhost:8000/alerts?limit=5"

# Combine filters
curl "http://localhost:8000/alerts?domain=fintech&severity=HIGH&limit=3"
```

---

### 🔍 Get Alert Detail

**Request:**
```bash
curl http://localhost:8000/alerts/42
```

**Expected Response:**
```json
{
  "alert_id": 42,
  "alert_type": "velocity_spike",
  "domain": "gaming",
  "actor_id": "A456789",
  "severity": "HIGH",
  "fraud_score": 85,
  "transaction_count": 5,
  "total_amount": 750.0,
  "first_seen": "2025-10-16T18:28:00",
  "last_seen": "2025-10-16T18:32:00",
  "transactions": [
    {
      "transaction_id": "TXN12345",
      "actor_id": "A456789",
      "domain": "gaming",
      "timestamp": "2025-10-16T18:28:15",
      "amount": 150.0,
      "transaction_data": {
        "ip_address": "45.142.23.45",
        "device_id": "DEV123",
        "payment_method": "credit_card"
      }
    }
  ],
  "metadata": {
    "window_seconds": 240,
    "baseline_avg": 1.2,
    "spike_ratio": 4.17,
    "patterns_detected": ["velocity_spike", "suspicious_ip"],
    "confidence": 87
  }
}
```

---

### 📊 Analytics Summary

**Request:**
```bash
curl http://localhost:8000/analytics/summary
```

**Expected Response:**
```json
{
  "period": "last_24h",
  "total_alerts": 127,
  "by_severity": {
    "CRITICAL": 12,
    "HIGH": 45,
    "MEDIUM": 58,
    "LOW": 12
  },
  "by_domain": {
    "gaming": 67,
    "ecommerce": 38,
    "fintech": 22
  },
  "avg_fraud_score": 72,
  "total_amount_flagged": 125000.50,
  "model_version": "v1.0-mocked"
}
```

---

### 📈 Domain-Specific Analytics

**Request:**
```bash
curl http://localhost:8000/analytics/domain/gaming
```

**Expected Response:**
```json
{
  "domain": "gaming",
  "period": "last_24h",
  "alert_count": 67,
  "avg_fraud_score": 78,
  "total_amount": 45000.0,
  "top_alert_types": [
    {"type": "velocity_spike", "count": 25},
    {"type": "account_takeover", "count": 18}
  ],
  "trend": "increasing"
}
```

**Valid domains:** `gaming`, `ecommerce`, `fintech`

---

### 📋 API Info

**Request:**
```bash
curl http://localhost:8000/
```

**Expected Response:**
```json
{
  "service": "PatternAlarm API Gateway",
  "version": "1.0.0-mocked",
  "status": "running",
  "endpoints": {
    "model_serving": "POST /predict",
    "health": "GET /health",
    "alerts": "GET /alerts, GET /alerts/{id}",
    "analytics": "GET /analytics/summary, GET /analytics/domain/{domain}"
  }
}
```

---

## 🐳 Docker Build
```bash
# Build image
docker build -t patternalarm-api-gateway .

# Run container
docker run -p 8000:8000 patternalarm-api-gateway

# Test
curl http://localhost:8000/health
```

---

## 📂 Project Structure
```
api-gateway/
├── main.py              # FastAPI app + endpoints
├── models.py            # Pydantic request/response models
├── requirements.txt     # Python dependencies
├── Dockerfile          # Container image
└── README.md           # This file
```

---

## 🔧 Development

### Interactive API Docs

Open browser: `http://localhost:8000/docs`

FastAPI automatically generates:
- **Swagger UI** - Interactive API testing
- **ReDoc** - API documentation

---

## ⚠️ Notes

- All responses are **mocked** (random data)
- `fraud_score` varies on each call
- Real PostgreSQL + Redis integration coming in Sprint 2
- Model serving uses mock logic, not real ML model yet

---

## 🎯 Next Steps

1. Deploy to ECS Fargate
2. Connect to real PostgreSQL (materialized views)
3. Add Redis caching
4. Load real ML model from S3

---

## 📊 Status

**Sprint 1:** ✅ Mocked endpoints ready  
**Sprint 2:** Real DB + ML model integration