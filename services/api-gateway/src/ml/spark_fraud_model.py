# src/ml/spark_fraud_model.py

from typing import Optional, Dict, Any
import time
import pandas as pd
from pyspark.sql import SparkSession
from pyspark.ml import PipelineModel
import mlflow.spark

from src.api.models.api_models import (
    BaseFraudModel,
    PredictRequest,
    PredictResponse,
    BatchPredictRequest,
    BatchPredictResponse
)


class SparkFraudModel(BaseFraudModel):
    """Spark ML model serving"""

    _instance: Optional['SparkFraudModel'] = None
    _model: Optional[PipelineModel] = None
    _spark: Optional[SparkSession] = None
    _labels: list = None
    _load_time: float = 0

    def __new__(cls, model_path: str = None, mlflow_uri: str = None):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._init(model_path, mlflow_uri)
        return cls._instance

    def _init(self, model_path: str = None, mlflow_uri: str = None):
        """Initialize once"""
        if self._model is not None:
            return

        start = time.time()

        self._spark = SparkSession.builder \
            .appName("FraudAPI") \
            .config("spark.driver.memory", "2g") \
            .getOrCreate()

        if mlflow_uri:
            self._model = mlflow.spark.load_model(mlflow_uri)
        elif model_path:
            self._model = PipelineModel.load(model_path)
        else:
            raise ValueError("Need model_path or mlflow_uri")

        self._labels = self._model.stages[2].labels
        self._load_time = time.time() - start
        print(f"✅ Spark model loaded ({self._load_time:.1f}s)")

    @property
    def ml_version(self) -> str:
        return "spark-v1.0"

    def _aggregate_features(self, request: PredictRequest) -> Dict:
        """Aggregate features from grouped transactions"""
        txns = [t.model_dump() for t in request.transactions]

        # Aggregate metrics
        amounts = [t['amount'] for t in txns]
        sessions = [t.get('session_length_sec', 0) for t in txns]
        timestamps = pd.to_datetime([t['timestamp'] for t in txns])

        total_amount = sum(amounts)
        avg_session = sum(sessions) / len(sessions) if sessions else 0
        fraud_txn_count = len(txns)
        time_delta_sec = (timestamps.max() - timestamps.min()).total_seconds()

        # First transaction temporal features
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

        # Payment method (map unknown to known)
        KNOWN_PAYMENTS = ['credit_card', 'debit_card', 'paypal', 'apple_pay', 'google_pay']
        payment = first_txn.get('payment_method', 'credit_card')
        if payment not in KNOWN_PAYMENTS:
            payment = 'credit_card'

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
            "payment_method": payment,
            "domain": request.domain,
            "fraud_first_seen": first_ts,
            "time_delta_sec": time_delta_sec
        }

    async def predict_single(self, request: PredictRequest) -> PredictResponse:
        """Predict fraud for single actor"""
        start = time.time()

        # Aggregate features
        features = self._aggregate_features(request)
        time_delta = features.pop('time_delta_sec')

        # Create DataFrame
        df = pd.DataFrame([features])
        spark_df = self._spark.createDataFrame(df)

        # Predict
        predictions_df = self._model.transform(spark_df)
        result = predictions_df.select("prediction", "probability").first()

        fraud_type = self._labels[int(result.prediction)]

        return PredictResponse(
            actor_id=request.actor_id,
            fraud_type=fraud_type,
            is_fraud=not fraud_type.startswith("regular"),
            confidence=float(max(result.probability)),
            transactions_analyzed=len(request.transactions),
            total_amount=round(features['amount'], 2),
            time_window_sec=round(time_delta, 2),
            ml_version=self.ml_version,
            inference_time_ms=round((time.time() - start) * 1000, 2)
        )

    async def predict_batch(self, request: BatchPredictRequest) -> BatchPredictResponse:
        """Predict fraud for multiple actors"""
        start = time.time()

        # Aggregate all features
        all_features = [
            self._aggregate_features(pred_req)
            for pred_req in request.predictions
        ]

        # Extract time deltas
        time_deltas = [f.pop('time_delta_sec') for f in all_features]

        # Create batch DataFrame
        df = pd.DataFrame(all_features)
        spark_df = self._spark.createDataFrame(df)

        # Batch predict
        predictions_df = self._model.transform(spark_df)
        results = predictions_df.select("prediction", "probability").collect()

        # Format responses
        predictions = []
        for i, (pred_req, row) in enumerate(zip(request.predictions, results)):
            fraud_type = self._labels[int(row.prediction)]

            predictions.append(PredictResponse(
                actor_id=pred_req.actor_id,
                fraud_type=fraud_type,
                is_fraud=not fraud_type.startswith("regular"),
                confidence=float(max(row.probability)),
                transactions_analyzed=len(pred_req.transactions),
                total_amount=round(all_features[i]['amount'], 2),
                time_window_sec=round(time_deltas[i], 2),
                ml_version=self.ml_version,
                inference_time_ms=0  # Filled at batch level
            ))

        total_time = (time.time() - start) * 1000

        return BatchPredictResponse(
            predictions=predictions,
            ml_version=self.ml_version,
            total_inference_time_ms=round(total_time, 2),
            actors_analyzed=len(predictions)
        )

    def health_check(self) -> Dict[str, Any]:
        return {
            "status": "healthy",
            "ml_type": "spark",
            "ml_version": self.ml_version,
            "classes": len(self._labels),
            "load_time_sec": round(self._load_time, 2)
        }