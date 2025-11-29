# src/services/predict_service.py

import logging
import os
from pathlib import Path
from fastapi import HTTPException

from src.api.models.api_models import (
    PredictRequest,
    PredictResponse,
    BatchPredictRequest,
    BatchPredictResponse
)
from src.ml.mlflow_fraud_model import MLflowFraudModel
from src.ml.spark_fraud_model import SparkFraudModel

logger = logging.getLogger(__name__)

# Load model
MODEL_TYPE = os.getenv("MODEL_TYPE", "spark")

if MODEL_TYPE == "mlflow":
    model_uri = os.getenv("MLFLOW_MODEL_URI")
    model = MLflowFraudModel(model_uri=model_uri)
    logger.info("✅ Using MLflow model")
else:
    model_path = Path(os.getenv("SPARK_MODEL_PATH", "../../data/models/fraud_detector_v1"))
    if model_path.exists():
        model = SparkFraudModel(model_path=str(model_path))
        logger.info(f"✅ Using Spark ML model")
    else:
        raise FileNotFoundError(f"Model not found at {model_path}")


class PredictService:
    """Prediction service"""

    def __init__(self):
        self.model = model

    async def predict_single(self, request: PredictRequest) -> PredictResponse:
        """Single actor prediction"""
        if not request.transactions:
            raise HTTPException(400, "No transactions provided")

        try:
            return await self.model.predict_single(request)
        except Exception as e:
            logger.error(f"❌ Prediction failed: {e}")
            raise HTTPException(500, f"Prediction failed: {str(e)}")

    async def predict_batch(self, request: BatchPredictRequest) -> BatchPredictResponse:
        """Batch actors prediction"""
        if not request.predictions:
            raise HTTPException(400, "No predictions provided")

        if len(request.predictions) > 100:
            raise HTTPException(400, "Max 100 actors per batch")

        try:
            response = await self.model.predict_batch(request)
            logger.info(f"✅ Predicted {response.actors_analyzed} actors in {response.total_inference_time_ms:.0f}ms")
            return response
        except Exception as e:
            logger.error(f"❌ Batch prediction failed: {e}")
            raise HTTPException(500, f"Prediction failed: {str(e)}")

    def health_check(self):
        return self.model.health_check()