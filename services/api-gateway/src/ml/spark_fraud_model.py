# src/ml/spark_fraud_model.py

import time
from typing import Optional, Dict, Any

import mlflow.spark
import pandas as pd
from pyspark.ml import PipelineModel
from pyspark.sql import SparkSession

# ✅ Import feature store components
from feature_store.features import FeatureEngineering
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

        # Download from S3 if needed
        if model_path and model_path.startswith("s3://"):
            import boto3
            import os

            local_path = "/tmp/fraud_model"
            s3 = boto3.client('s3')

            # Parse s3://bucket/prefix
            path_parts = model_path.replace("s3://", "").split("/", 1)
            bucket = path_parts[0]
            prefix = path_parts[1] if len(path_parts) > 1 else ""

            # Download all model files
            os.makedirs(local_path, exist_ok=True)
            paginator = s3.get_paginator('list_objects_v2')
            for page in paginator.paginate(Bucket=bucket, Prefix=prefix):
                for obj in page.get('Contents', []):
                    key = obj['Key']
                    relative_path = key[len(prefix):].lstrip("/")
                    local_file = os.path.join(local_path, relative_path)
                    os.makedirs(os.path.dirname(local_file), exist_ok=True)
                    s3.download_file(bucket, key, local_file)

            model_path = local_path
            print(f"✅ Downloaded model from S3 to {local_path}")

        self._spark = SparkSession.builder \
            .appName("FraudAPI") \
            .config("spark.driver.memory", "1g") \
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

    async def predict_single(self, request: PredictRequest) -> PredictResponse:
        """Predict fraud for single actor"""
        start = time.time()

        # ✅ Convert to feature store entity
        actor = request.to_actor_transactions()  # ✅ Use method from PredictRequest

        # ✅ Use feature store for feature extraction
        features = FeatureEngineering.extract_features_pandas(actor)
        time_delta = features.pop('time_delta_sec')
        features.pop('fraud_first_seen')

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

        # ✅ Convert all to feature store entities
        actors = [pred_req.to_actor_transactions() for pred_req in request.predictions]

        # ✅ Extract features using feature store
        all_features = [
            FeatureEngineering.extract_features_pandas(actor)
            for actor in actors
        ]

        # Extract metadata
        time_deltas = [f.pop('time_delta_sec') for f in all_features]
        for f in all_features:
            f.pop('fraud_first_seen')

        # Batch predict
        df = pd.DataFrame(all_features)
        spark_df = self._spark.createDataFrame(df)
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
                inference_time_ms=0
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