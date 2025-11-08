# src/api/routes/predict_router.py

from fastapi import APIRouter, Depends
from src.api.models.api_models import (
    PredictRequest,
    PredictResponse,
    BatchPredictRequest,
    BatchPredictResponse
)
from src.services.predict_service import PredictService

predict_router = APIRouter(tags=["predict"])


def get_service() -> PredictService:
    return PredictService()


@predict_router.post("/predict", response_model=PredictResponse)
async def predict_single(
        request: PredictRequest,
        service: PredictService = Depends(get_service)
):
    """
    Single actor fraud prediction

    **Input:** 1 actor with grouped transactions
    **Output:** 1 fraud prediction
    """
    return await service.predict_single(request)


@predict_router.post("/predict/batch", response_model=BatchPredictResponse)
async def predict_batch(
        request: BatchPredictRequest,
        service: PredictService = Depends(get_service)
):
    """
    Batch actors fraud prediction

    **Input:** Multiple actors (max 100)
    **Output:** Predictions for all actors
    """
    return await service.predict_batch(request)


@predict_router.get("/predict/health")
async def health(service: PredictService = Depends(get_service)):
    return service.health_check()