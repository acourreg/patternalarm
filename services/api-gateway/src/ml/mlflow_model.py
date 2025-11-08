"""MLflow Model Loader (Optimized)"""
import logging
import mlflow
import pandas as pd
from pathlib import Path
from typing import Dict, Any, Optional
from datetime import datetime

logger = logging.getLogger(__name__)

class MLflowFraudModel:
    _instance: Optional['MLflowFraudModel'] = None
    _model: Optional[Any] = None
    _load_time: float = 0

    def __init__(self):
        if MLflowFraudModel._model is None:
            start = datetime.now()

            # Read model URI
            uri_file = Path("../../data/models/mlflow_model_uri.txt")
            model_uri = uri_file.read_text().strip()

            # Set tracking URI
            mlflow.set_tracking_uri("file:../../data/models/mlflow_tracking")

            logger.info(f"📦 Loading model: {model_uri}")

            # Load model (THIS IS SLOW - 1-2 seconds)
            MLflowFraudModel._model = mlflow.pyfunc.load_model(model_uri)

            load_time = (datetime.now() - start).total_seconds()
            MLflowFraudModel._load_time = load_time

            logger.info(f"✅ Model loaded in {load_time:.2f}s")

            # ✅ PRE-WARM: Run dummy prediction to initialize Spark
            logger.info("🔥 Pre-warming model...")
            dummy_features = {
                "actor_id": "warmup",
                "domain": "gaming",
                "amount": 100.0,
                "fraud_txn_count": 1,
                "session_length_sec": 600.0,
                "country_mismatch": 0,
                "hour_of_day": 12,
                "day_of_week": 3,
                "is_weekend": 0,
                "is_near_threshold": 0,
                "involves_high_risk_country": 0,
                "is_rapid_session": 0,
                "amount_per_txn": 100.0,
                "session_efficiency": 0.17,
                "night_rapid_combo": 0,
                "payment_method": "credit_card"
            }

            warmup_start = datetime.now()
            df = pd.DataFrame([dummy_features])
            _ = MLflowFraudModel._model.predict(df)
            warmup_time = (datetime.now() - warmup_start).total_seconds()

            logger.info(f"✅ Model pre-warmed in {warmup_time:.2f}s")
            logger.info(f"🎯 Total startup time: {load_time + warmup_time:.2f}s")

    @classmethod
    def get_instance(cls):
        if cls._instance is None:
            cls._instance = cls()
        return cls._instance

    async def predict(self, request_data: Dict) -> Dict:
        start = datetime.now()

        try:
            # Extract features
            features = self._extract_features(request_data)

            # Predict (should be fast now - <100ms)
            feature_start = datetime.now()
            df = pd.DataFrame([features])
            prediction_idx = int(self._model.predict(df)[0])
            prediction_time = (datetime.now() - feature_start).total_seconds() * 1000

            logger.debug(f"⚡ Prediction time: {prediction_time:.0f}ms")

            # Map to score AND type
            fraud_score, fraud_type = self._map_prediction(prediction_idx)

            total_time = (datetime.now() - start).total_seconds() * 1000

            return {
                "fraud_score": fraud_score,
                "fraud_type": fraud_type,
                "model_version": "v1.0-mlflow",
                "inference_time_ms": int(total_time),
                "transactions_analyzed": request_data['transaction_count']
            }
        except Exception as e:
            logger.error(f"❌ Prediction failed: {e}")
            return self._fallback(request_data)

    def _map_prediction(self, prediction_idx: int) -> tuple[int, str]:  # ✅ Already returns tuple
        """Map prediction to (score, type)"""
        fraud_types = [
            "regular",
            "fraud_account_takeover",
            "fraud_card_testing",
            "fraud_chargeback_fraud",
            "fraud_friendly_fraud",
            "fraud_gold_farming",
            "fraud_money_laundering",
            "fraud_structuring"
        ]

        fraud_type = fraud_types[prediction_idx] if prediction_idx < len(fraud_types) else "unknown"
        fraud_score = 20 if prediction_idx == 0 else 70 + (prediction_idx * 3)

        return fraud_score, fraud_type

    def _extract_features(self, data: Dict) -> Dict:
        txns = data['transactions']
        sessions = [t.get('sessionLengthSec') or t.get('sessionDurationSec') or 0 for t in txns]
        avg_session = sum(sessions) / len(sessions) if sessions else 0

        payment = next((t.get('paymentMethod') for t in txns if t.get('paymentMethod')), "unknown")

        hour = data['window_start'].hour if hasattr(data['window_start'], 'hour') else 12
        dow = data['window_start'].isoweekday() if hasattr(data['window_start'], 'isoweekday') else 3

        return {
            "actor_id": data['actor_id'],
            "domain": data['domain'],
            "amount": float(data['total_amount']),
            "fraud_txn_count": int(data['transaction_count']),
            "session_length_sec": float(avg_session),
            "country_mismatch": 0,
            "hour_of_day": hour,
            "day_of_week": dow,
            "is_weekend": 1 if dow in [6, 7] else 0,
            "is_near_threshold": 1 if 9500 <= data['total_amount'] < 10000 else 0,
            "involves_high_risk_country": 0,
            "is_rapid_session": 1 if avg_session < 120 else 0,
            "amount_per_txn": data['total_amount'] / (data['transaction_count'] + 1),
            "session_efficiency": data['total_amount'] / (avg_session + 1),
            "night_rapid_combo": 1 if ((hour < 6 or hour > 22) and avg_session < 120) else 0,
            "payment_method": payment
        }

    def _fallback(self, data: Dict) -> Dict:
        return {
            "fraud_score": 50,
            "fraud_type": "unknown",  # ✅ ADD THIS
            "model_version": "v1.0-fallback",
            "inference_time_ms": 5,
            "transactions_analyzed": data['transaction_count']
        }