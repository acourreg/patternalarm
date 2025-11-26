# services/airflow/tests/test_train_model.py
import pytest
import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'jobs'))


class TestTrainModel:
    """Tests for train_model.py job"""

    def test_job_imports(self):
        """✅ Job imports work."""
        from train_model import main
        assert main is not None

    def test_train_pipeline(self, spark, sample_features, temp_dir):
        """✅ Training pipeline works end-to-end."""
        from pyspark.ml.feature import VectorAssembler, StringIndexer
        from pyspark.ml.classification import RandomForestClassifier
        from pyspark.ml import Pipeline

        df = spark.read.parquet(sample_features)

        feature_cols = ['amount', 'transaction_count', 'amount_per_transaction',
                        'time_delta_sec', 'velocity_per_sec']

        pipeline = Pipeline(stages=[
            StringIndexer(inputCol="fraud_label", outputCol="label"),
            VectorAssembler(inputCols=feature_cols, outputCol="features"),
            RandomForestClassifier(labelCol="label", featuresCol="features", numTrees=10, seed=42)
        ])

        model = pipeline.fit(df)
        model_path = f"{temp_dir}/model"
        model.write().overwrite().save(model_path)

        from pyspark.ml import PipelineModel
        assert PipelineModel.load(model_path) is not None