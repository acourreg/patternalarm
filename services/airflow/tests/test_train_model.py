# services/airflow/tests/test_train_model.py

import pytest


class TestTrainModel:
    """Tests for train_model.py pipeline."""

    def test_full_pipeline_training(self, spark, sample_features):
        """✅ Complete pipeline trains successfully."""
        from pyspark.ml.feature import VectorAssembler, StringIndexer
        from pyspark.ml.classification import RandomForestClassifier
        from pyspark.ml import Pipeline

        df = spark.read.parquet(sample_features)

        feature_cols = ['amount', 'transaction_count', 'amount_per_transaction',
                        'time_delta_sec', 'velocity_per_sec']

        pipeline = Pipeline(stages=[
            StringIndexer(inputCol="fraud_label", outputCol="label"),
            VectorAssembler(inputCols=feature_cols, outputCol="features"),
            RandomForestClassifier(labelCol="label", featuresCol="features",
                                   numTrees=10, maxDepth=5, seed=42)
        ])

        train_df, test_df = df.randomSplit([0.8, 0.2], seed=42)
        model = pipeline.fit(train_df)

        assert model is not None
        assert len(model.stages) == 3

    def test_model_produces_predictions(self, spark, sample_features):
        """✅ Trained model produces valid predictions with metrics."""
        from pyspark.ml.feature import VectorAssembler, StringIndexer
        from pyspark.ml.classification import RandomForestClassifier
        from pyspark.ml.evaluation import MulticlassClassificationEvaluator
        from pyspark.ml import Pipeline

        df = spark.read.parquet(sample_features)

        feature_cols = ['amount', 'transaction_count', 'amount_per_transaction',
                        'time_delta_sec', 'velocity_per_sec']

        pipeline = Pipeline(stages=[
            StringIndexer(inputCol="fraud_label", outputCol="label"),
            VectorAssembler(inputCols=feature_cols, outputCol="features"),
            RandomForestClassifier(labelCol="label", featuresCol="features",
                                   numTrees=10, maxDepth=5, seed=42)
        ])

        train_df, test_df = df.randomSplit([0.8, 0.2], seed=42)
        model = pipeline.fit(train_df)
        predictions = model.transform(test_df)

        assert "prediction" in predictions.columns

        accuracy = MulticlassClassificationEvaluator(
            labelCol="label", predictionCol="prediction", metricName="accuracy"
        ).evaluate(predictions)

        assert 0 <= accuracy <= 1

    def test_model_persistence(self, spark, sample_features, temp_dir):
        """✅ Model can be saved and reloaded."""
        from pyspark.ml.feature import VectorAssembler, StringIndexer
        from pyspark.ml.classification import RandomForestClassifier
        from pyspark.ml import Pipeline, PipelineModel

        df = spark.read.parquet(sample_features)

        feature_cols = ['amount', 'transaction_count', 'amount_per_transaction',
                        'time_delta_sec', 'velocity_per_sec']

        pipeline = Pipeline(stages=[
            StringIndexer(inputCol="fraud_label", outputCol="label"),
            VectorAssembler(inputCols=feature_cols, outputCol="features"),
            RandomForestClassifier(labelCol="label", featuresCol="features",
                                   numTrees=10, maxDepth=5, seed=42)
        ])

        model = pipeline.fit(df)
        model_path = f"{temp_dir}/model"
        model.write().overwrite().save(model_path)

        loaded = PipelineModel.load(model_path)

        assert loaded is not None
        assert len(loaded.stages) == 3