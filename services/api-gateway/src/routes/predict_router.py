"""
ML prediction route (REAL Spark ML model)
"""
from fastapi import APIRouter, Depends
from src.api.models.api_models import PredictRequest, PredictResponse
from src.services.alert_service import AlertService
from sqlalchemy.ext.asyncio import AsyncSession
from src.repositories.alert_repository import AlertRepository
from src.database.postgres_client import get_db

predict_router = APIRouter(tags=["predict"])


# Dependency to get service
async def get_alert_service(db: AsyncSession = Depends(get_db)) -> AlertService:
    """Create alert service with repository"""
    repository = AlertRepository(db)
    return AlertService(repository)



@predict_router.post("/predict", response_model=PredictResponse)
async def predict(
    request: PredictRequest,
    service: AlertService = Depends(get_alert_service)
):
    """
    ML model prediction endpoint (REAL SPARK ML)
    Flink sends aggregated features + raw transactions, receives fraud_score

    Model: RandomForest (150 trees, depth 16)
    Accuracy: 97.5%
    Classes: 8 (1 regular + 7 fraud types)
    """
    return await service.predict_fraud(request)