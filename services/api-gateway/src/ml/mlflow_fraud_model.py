# src/ml/mlflow_fraud_model.py

from typing import Optional, Dict, Any
import time
import logging
import mlflow
import pandas as pd
from pathlib import Path

from src.api.models.api_models import (
    BaseFraudModel,
    PredictRequest,
    PredictResponse,
    BatchPredictRequest,
    BatchPredictResponse
)

logger = logging.getLogger(__name__)


class MLflowFraudModel(BaseFraudModel):
    """MLflow model serving"""

    _instance: Optional['MLflowFraudModel'] = None
    _model: Optional[Any] = None
    _load_time: float = 0

    def __new__(cls, model_uri: str = None):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._init(model_uri)
        return cls._instance

    def _init(self, model_uri: str = None):
        """Initialize once"""
        if self._model is not None:
            return

        start = time.time()

        if not model_uri:
            uri_file = Path("../../data/models/mlflow_model_uri.txt")
            if uri_file.exists():
                model_uri = uri_file.read_text().strip()
            else:
                self._model = "MOCKED"
                return

        mlflow.set_tracking_uri("file:../../data/models/mlflow_tracking")
        self._model = mlflow.pyfunc.load_model(model_uri)
        self._load_time = time.time() - start
        print(f"✅ MLflow model loaded ({self._load_time:.1f}s)")

    @property
    def ml_version(self) -> str:
        return "mlflow-v1.0"

    def _aggregate_features(self, request: PredictRequest) -> Dict:
        """Aggregate features from grouped transactions"""
        from datetime import datetime

        txns = [t.model_dump() for t in request.transactions]

        # Aggregate metrics
        amounts = [t['amount'] for t in txns]
        sessions = [t.get('session_length_sec', 0) for t in txns]
        timestamps = [datetime.fromisoformat(t['timestamp']) if isinstance(t['timestamp'], str)
                      else t['timestamp'] for t in txns]

        total_amount = sum(amounts)
        avg_session = sum(sessions) / len(sessions) if sessions else 0
        fraud_txn_count = len(txns)
        time_delta_sec = (max(timestamps) - min(timestamps)).total_seconds()

        # First transaction
        first_txn = txns[0]
        first_ts = timestamps[0]

        # Country analysis
        HIGH_RISK = ['KY', 'PA', 'BZ', 'VG']
        country_mismatch = any(
            t.get('country_from') and t.get('country_to')
            and t['country_from'] != t['country_to']
            for t in txns
        )
        involves_high_risk = any(
            t.get('country_from') in HIGH_RISK or t.get('country_to') in HIGH_RISK
            for t in txns
        )

        return {
            "amount": total_amount,
            "fraud_txn_count": fraud_txn_count,
            "session_length_sec": avg_session,
            "country_mismatch": 1 if country_mismatch else 0,
            "hour_of_day": first_ts.hour,
            "day_of_week": first_ts.isoweekday(),
            "is_weekend": 1 if first_ts.isoweekday() in [6, 7] else 0,
            "is_near_threshold": 1 if 9500 <= total_amount < 10000 else 0,
            "involves_high_risk_country": 1 if involves_high_risk else 0,
            "is_rapid_session": 1 if avg_session < 120 else 0,
            "amount_per_txn": total_amount / fraud_txn_count,
            "session_efficiency": total_amount / (avg_session + 1),
            "night_rapid_combo": 1 if ((first_ts.hour < 6 or first_ts.hour > 22) and avg_session < 120) else 0,
            "payment_method": first_txn.get('payment_method', 'credit_card'),
            "time_delta_sec": time_delta_sec
        }

    async def predict_single(self, request: PredictRequest) -> PredictResponse:
        """Single actor prediction"""
        start = time.time()

        if self._model == "MOCKED":
            fraud_type = "regular"
            confidence = 0.95
        else:
            features = self._aggregate_features(request)
            time_delta = features.pop('time_delta_sec')

            df = pd.DataFrame([features])
            pred = self._model.predict(df)

            idx = int(pred[0])
            fraud_types = ["regular", "fraud_account_takeover", "fraud_card_testing",
                           "fraud_chargeback_fraud", "fraud_structuring", "fraud_money_laundering"]
            fraud_type = fraud_types[idx] if idx < len(fraud_types) else "unknown"
            confidence = 0.95 if fraud_type == "regular" else 0.75

        return PredictResponse(
            actor_id=request.actor_id,
            fraud_type=fraud_type,
            is_fraud=not fraud_type.startswith("regular"),
            confidence=confidence,
            transactions_analyzed=len(request.transactions),
            total_amount=sum(t.amount for t in request.transactions),
            time_window_sec=0.0,  # Mocked
            ml_version=self.ml_version,
            inference_time_ms=round((time.time() - start) * 1000, 2)
        )

    async def predict_batch(self, request: BatchPredictRequest) -> BatchPredictResponse:
        """Batch actors prediction"""
        start = time.time()

        predictions = []
        for pred_req in request.predictions:
            pred = await self.predict_single(pred_req)
            predictions.append(pred)

        return BatchPredictResponse(
            predictions=predictions,
            ml_version=self.ml_version,
            total_inference_time_ms=round((time.time() - start) * 1000, 2),
            actors_analyzed=len(predictions)
        )

    def health_check(self) -> Dict[str, Any]:
        return {
            "status": "mock" if self._model == "MOCKED" else "healthy",
            "ml_type": "mlflow",
            "ml_version": self.ml_version,
            "load_time_sec": round(self._load_time, 2)
        }