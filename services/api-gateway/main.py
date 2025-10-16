"""
FastAPI Mock Service
- Model serving for Flink (POST /predict)
- Query layer for Dashboard (GET /alerts, /analytics)
"""
from fastapi import FastAPI, HTTPException
from datetime import datetime, timedelta
import random
from typing import Optional

from models import (
    PredictRequest, PredictResponse,
    HealthResponse, AlertsResponse, Alert, AlertDetail,
    AnalyticsSummary, DomainAnalytics, Transaction
)

app = FastAPI(
    title="PatternAlarm API Gateway",
    description="Mock ML serving + Query layer",
    version="1.0.0-mocked"
)



# ============================================================================
# MODEL SERVING ENDPOINT (Flink calls this)
# ============================================================================


@app.post("/predict", response_model=PredictResponse)
async def predict(request: PredictRequest):
    """
    ML model prediction endpoint (MOCKED)
    Flink sends aggregated features + raw transactions, receives fraud_score
    """
    # Mock scoring logic based on aggregated features
    base_score = min(100, (request.transaction_count * 15) + int(request.total_amount / 100))

    # Add scoring based on transaction patterns
    if len(request.transactions) > 0:
        # Check for suspicious IPs (mocked logic)
        suspicious_ips = sum(1 for t in request.transactions if t.ip_address and t.ip_address.startswith("45.142"))
        base_score += suspicious_ips * 5

        # Check for high-value transactions
        high_value_count = sum(1 for t in request.transactions if t.amount > 500)
        base_score += high_value_count * 3

    # Add random noise
    noise = random.randint(-10, 10)
    fraud_score = max(0, min(100, base_score + noise))

    return PredictResponse(
        fraud_score=fraud_score,
        model_version="v1.0-mocked",
        inference_time_ms=random.randint(8, 20),
        transactions_analyzed=len(request.transactions)
    )


# ============================================================================
# HEALTH CHECK
# ============================================================================

@app.get("/health", response_model=HealthResponse)
async def health():
    """System health check"""
    return HealthResponse(
        status="healthy",
        database="connected",
        redis="connected",
        model_loaded=True,
        model_version="v1.0-mocked",
        timestamp=datetime.now()
    )


# ============================================================================
# ALERTS ENDPOINTS
# ============================================================================

@app.get("/alerts", response_model=AlertsResponse)
async def get_alerts(
        domain: Optional[str] = None,
        severity: Optional[str] = None,
        limit: int = 10
):
    """
    Get list of alerts (MOCKED)
    Query params: domain, severity, limit
    """
    # Generate mock alerts
    alerts = [generate_mock_alert(i) for i in range(1, limit + 1)]

    # Apply filters
    if domain:
        alerts = [a for a in alerts if a.domain == domain]
    if severity:
        alerts = [a for a in alerts if a.severity == severity]

    return AlertsResponse(
        alerts=alerts,
        total=len(alerts),
        page=1
    )


@app.get("/alerts/{alert_id}", response_model=AlertDetail)
async def get_alert_detail(alert_id: int):
    """
    Get alert detail with nested transactions (MOCKED)
    """
    alert = generate_mock_alert(alert_id)
    transactions = generate_mock_transactions(
        count=alert.transaction_count,
        actor_id=alert.actor_id,
        domain=alert.domain
    )

    return AlertDetail(
        **alert.model_dump(),
        transactions=transactions,
        metadata={
            "window_seconds": 240,
            "baseline_avg": 1.2,
            "spike_ratio": round(random.uniform(3.0, 5.0), 2),
            "patterns_detected": ["velocity_spike", "suspicious_ip"],
            "confidence": random.randint(75, 95)
        }
    )


# ============================================================================
# ANALYTICS ENDPOINTS
# ============================================================================

@app.get("/analytics/summary", response_model=AnalyticsSummary)
async def get_analytics_summary():
    """
    Overall analytics summary (MOCKED)
    """
    return AnalyticsSummary(
        period="last_24h",
        total_alerts=random.randint(100, 200),
        by_severity={
            "CRITICAL": random.randint(10, 20),
            "HIGH": random.randint(40, 60),
            "MEDIUM": random.randint(50, 80),
            "LOW": random.randint(10, 30)
        },
        by_domain={
            "gaming": random.randint(50, 80),
            "ecommerce": random.randint(30, 50),
            "fintech": random.randint(20, 40)
        },
        avg_fraud_score=random.randint(70, 80),
        total_amount_flagged=round(random.uniform(100000, 200000), 2)
    )


@app.get("/analytics/domain/{domain}", response_model=DomainAnalytics)
async def get_domain_analytics(domain: str):
    """
    Domain-specific analytics (MOCKED)
    """
    if domain not in ["gaming", "ecommerce", "fintech"]:
        raise HTTPException(status_code=404, detail="Domain not found")

    return DomainAnalytics(
        domain=domain,
        period="last_24h",
        alert_count=random.randint(30, 80),
        avg_fraud_score=random.randint(70, 85),
        total_amount=round(random.uniform(30000, 80000), 2),
        top_alert_types=[
            {"type": "velocity_spike", "count": random.randint(10, 30)},
            {"type": "account_takeover", "count": random.randint(5, 20)}
        ],
        trend=random.choice(["increasing", "stable", "decreasing"])
    )


# ============================================================================
# ROOT
# ============================================================================

@app.get("/")
async def root():
    """API info"""
    return {
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


# ============================================================================
# MOCK DATA GENERATORS
# ============================================================================

def generate_mock_transactions(count: int, actor_id: str, domain: str) -> list[Transaction]:
    """Generate mock transactions for an alert"""
    transactions = []
    base_time = datetime.now() - timedelta(minutes=5)

    for i in range(count):
        transactions.append(Transaction(
            transaction_id=f"TXN{random.randint(10000, 99999)}",
            actor_id=actor_id,
            domain=domain,
            timestamp=base_time + timedelta(seconds=i * 30),
            amount=round(random.uniform(50, 500), 2),
            transaction_data={
                "ip_address": f"45.142.{random.randint(1, 255)}.{random.randint(1, 255)}",
                "device_id": f"DEV{random.randint(1000, 9999)}",
                "payment_method": random.choice(["credit_card", "paypal", "crypto"])
            }
        ))
    return transactions


def generate_mock_alert(alert_id: int) -> Alert:
    """Generate a single mock alert"""
    domain = random.choice(["gaming", "ecommerce", "fintech"])
    severity = random.choice(["CRITICAL", "HIGH", "MEDIUM", "LOW"])
    actor_id = f"A{random.randint(100000, 999999)}"

    return Alert(
        alert_id=alert_id,
        alert_type=random.choice(["velocity_spike", "account_takeover", "suspicious_amount"]),
        domain=domain,
        actor_id=actor_id,
        severity=severity,
        fraud_score=random.randint(60, 95),
        transaction_count=random.randint(3, 8),
        total_amount=round(random.uniform(200, 2000), 2),
        first_seen=datetime.now() - timedelta(minutes=random.randint(5, 30)),
        last_seen=datetime.now() - timedelta(minutes=random.randint(0, 5))
    )



if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)