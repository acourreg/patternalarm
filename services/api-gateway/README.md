## 🔌 API Reference

### Base URL
```
http://api-gateway.patternalarm.local:8080
```

---

### Health Check

```bash
GET /health
```

**Response:**
```json
{"status": "healthy"}
```

---

### Fraud Prediction

```bash
POST /predict
Content-Type: application/json
```

**Request Body:**
```json
{
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
      "device_id": "DEV123",
      "currency": "USD",
      "payment_method": "credit_card",
      "domain": "gaming"
    },
    {
      "transaction_id": "TXN002",
      "timestamp": "2025-10-16T18:00:45",
      "amount": 200.0,
      "ip_address": "192.168.1.1",
      "device_id": "DEV123",
      "currency": "USD",
      "payment_method": "credit_card",
      "domain": "gaming"
    }
  ]
}
```

**Response:**
```json
{
  "actor_id": "A123456",
  "fraud_type": "regular",
  "is_fraud": false,
  "confidence": 0.4466,
  "transactions_analyzed": 2,
  "total_amount": 350.0,
  "time_window_sec": 30.0,
  "ml_version": "spark-v1.0",
  "inference_time_ms": 2869.58
}
```

| Field | Type | Description |
|-------|------|-------------|
| `is_fraud` | bool | Fraud prediction result |
| `confidence` | float | ML confidence score (0-1) |
| `fraud_type` | string | `regular` or detected pattern type |
| `ml_version` | string | Model version used |
| `inference_time_ms` | float | Prediction latency |

---

### Get Alerts

```bash
GET /alerts?domain={domain}&severity={severity}&limit={limit}&page={page}
```

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `domain` | string | - | Filter by domain: `gaming`, `fintech`, `ecommerce` |
| `severity` | string | - | Filter by severity: `low`, `medium`, `high`, `critical` |
| `limit` | int | 10 | Results per page |
| `page` | int | 1 | Page number |

**Response:**
```json
{
  "alerts": [
    {
      "alertId": 1,
      "alertType": "velocity_spike",
      "domain": "gaming",
      "actorId": "PLAYER_123",
      "severity": "high",
      "fraudScore": 85,
      "transactionCount": 15,
      "totalAmount": 2500.0,
      "firstSeen": "2025-10-16T18:00:00",
      "lastSeen": "2025-10-16T18:02:00"
    }
  ],
  "total": 42,
  "page": 1
}
```

---

### Get Alert Detail

```bash
GET /alerts/{alert_id}
```

**Response:**
```json
{
  "alert": {
    "alertId": 1,
    "alertType": "velocity_spike",
    "domain": "gaming",
    "actorId": "PLAYER_123",
    "severity": "high",
    "fraudScore": 85,
    "transactionCount": 15,
    "totalAmount": 2500.0,
    "firstSeen": "2025-10-16T18:00:00",
    "lastSeen": "2025-10-16T18:02:00",
    "windowSeconds": 120,
    "baselineAvg": 150.0,
    "patternsDetected": ["rapid_fire", "amount_anomaly"],
    "confidence": 92,
    "modelVersion": "spark-v1.0",
    "inferenceTimeMs": 45
  },
  "transactions": [
    {
      "transactionId": "TXN001",
      "domain": "gaming",
      "timestamp": "2025-10-16T18:00:15",
      "actorId": "PLAYER_123",
      "amount": 150.0,
      "currency": "USD",
      "ipAddress": "45.142.23.45",
      "pattern": "velocity_spike",
      "isFraud": true
    }
  ]
}
```

---

### Velocity Analytics

```bash
GET /analytics/velocity?bucket_size_seconds={bucket}&sliding_window_rows={rows}
```

**Query Parameters:**

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| `bucket_size_seconds` | int | 5 | Time bucket size for aggregation |
| `sliding_window_rows` | int | 4 | Sliding window size for smoothing |

**Response:**
```json
{
  "data_points": [
    {
      "bucket": "2025-10-16T18:00:00",
      "domain": "gaming",
      "y1_velocity": 12,
      "y2_avg_amount": 245.50
    }
  ],
  "bucket_size_seconds": 5,
  "total_alerts": 156,
  "domains": ["gaming", "fintech", "ecommerce"]
}
```

> ℹ️ **Cache:** Results cached in Redis for 10s TTL

---

### Supported Domains

| Domain | Description | Actor Field |
|--------|-------------|-------------|
| `gaming` | In-game purchases, virtual items | `playerId` |
| `fintech` | Bank transfers, payments | `accountFrom` |
| `ecommerce` | Online shopping | `userId` |

---

### Error Responses

```json
{
  "detail": "Error message here"
}
```

| Status | Description |
|--------|-------------|
| `400` | Invalid request body |
| `404` | Alert not found |
| `500` | Prediction failed / Internal error |