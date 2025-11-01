"""
ML prediction route (mocked)
"""
from fastapi import APIRouter
import random

from src.api.models.api_models import PredictRequest, PredictResponse

predict_router = APIRouter(tags=["predict"])


@predict_router.post("/predict", response_model=PredictResponse)
async def predict(request: PredictRequest):
    """
    ML model prediction endpoint (MOCKED)
    Flink sends aggregated features + raw transactions, receives fraud_score
    """
    # Mock scoring logic
    base_score = min(100, (request.transaction_count * 15) + int(request.total_amount / 100))

    if len(request.transactions) > 0:
        suspicious_ips = sum(1 for t in request.transactions if t.ipAddress and t.ipAddress.startswith("45.142"))
        base_score += suspicious_ips * 5
        high_value_count = sum(1 for t in request.transactions if t.amount > 500)
        base_score += high_value_count * 3

    noise = random.randint(-10, 10)
    fraud_score = max(0, min(100, base_score + noise))

    # TODO: Force high scores 50% of time for testing
    if random.random() < 0.5:
        fraud_score = random.randint(70, 95)

    return PredictResponse(
        fraud_score=fraud_score,
        model_version="v1.0-mocked",
        inference_time_ms=random.randint(8, 20),
        transactions_analyzed=len(request.transactions)
    )