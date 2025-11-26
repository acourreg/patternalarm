# services/airflow/tests/test_train_model.py

import pytest
import os
import sys
import json

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'jobs'))


class TestTrainModel:
    """Tests for train_model.py"""

    def test_load_features(self, spark, sample_features):
        """✅ Can load feature parquet."""
        df = spark.read.parquet(sample_features)

        assert df.count() == 50
        assert "amount" in df.columns
        assert "fraud_label" in df.columns
        print(f"✅ Loaded {df.count()} feature vectors")

    def test_string_indexer(self, spark, sample_features):
        """✅ StringIndexer converts labels."""
        from pyspark.ml.feature import StringIndexer

        df = spark.read.parquet(sample_features)

        indexer = StringIndexer(inputCol="fraud_label", outputCol="label")
        model = indexer.fit(df)
        indexed_df = model.transform(df)

        assert "label" in indexed_df.columns
        labels = indexed_df.select("label").distinct().collect()
        assert len(labels) == 2  # fraud + legitimate
        print(f"✅ Labels indexed: {[r.label for r in labels]}")

    def test_vector_assembler(self, spark, sample_features):
        """✅ VectorAssembler creates feature vectors."""
        from pyspark.ml.feature import VectorAssembler

        df = spark.read.parquet(sample_features)

        feature_cols = ['amount', 'transaction_count', 'amount_per_transaction']

        assembler = VectorAssembler(
            inputCols=feature_cols,
            outputCol="features_raw"
        )

        assembled_df = assembler.transform(df)

        assert "features_raw" in assembled_df.columns
        print("✅ Features assembled")

    def test_train_test_split(self, spark, sample_features):
        """✅ Train/test split works."""
        df = spark.read.parquet(sample_features)

        train_df, test_df = df.randomSplit([0.8, 0.2], seed=42)

        total = train_df.count() + test_df.count()
        assert total == 50
        assert train_df.count() > test_df.count()
        print(f"✅ Split: train={train_df.count()}, test={test_df.count()}")

    def test_random_forest_training(self, spark, sample_features):
        """✅ RandomForest trains successfully."""
        from pyspark.ml.feature import VectorAssembler, StringIndexer
        from pyspark.ml.classification import RandomForestClassifier
        from pyspark.ml import Pipeline

        df = spark.read.parquet(sample_features)

        feature_cols = ['amount', 'transaction_count', 'amount_per_transaction',
                        'time_delta_sec', 'velocity_per_sec']

        indexer = StringIndexer(inputCol="fraud_label", outputCol="label")
        assembler = VectorAssembler(inputCols=feature_cols, outputCol="features")
        rf = RandomForestClassifier(labelCol="label", featuresCol="features",
                                    numTrees=10, maxDepth=5, seed=42)

        pipeline = Pipeline(stages=[indexer, assembler, rf])

        train_df, test_df = df.randomSplit([0.8, 0.2], seed=42)
        model = pipeline.fit(train_df)

        assert model is not None
        print("✅ Model trained successfully")

    def test_model_evaluation(self, spark, sample_features):
        """✅ Model evaluation produces metrics."""
        from pyspark.ml.feature import VectorAssembler, StringIndexer
        from pyspark.ml.classification import RandomForestClassifier
        from pyspark.ml.evaluation import MulticlassClassificationEvaluator
        from pyspark.ml import Pipeline

        df = spark.read.parquet(sample_features)

        feature_cols = ['amount', 'transaction_count', 'amount_per_transaction',
                        'time_delta_sec', 'velocity_per_sec']

        indexer = StringIndexer(inputCol="fraud_label", outputCol="label")
        assembler = VectorAssembler(inputCols=feature_cols, outputCol="features")
        rf = RandomForestClassifier(labelCol="label", featuresCol="features",
                                    numTrees=10, maxDepth=5, seed=42)

        pipeline = Pipeline(stages=[indexer, assembler, rf])

        train_df, test_df = df.randomSplit([0.8, 0.2], seed=42)
        model = pipeline.fit(train_df)
        predictions = model.transform(test_df)

        evaluator = MulticlassClassificationEvaluator(
            labelCol="label", predictionCol="prediction", metricName="accuracy"
        )

        accuracy = evaluator.evaluate(predictions)

        assert 0 <= accuracy <= 1
        print(f"✅ Accuracy: {accuracy:.4f}")

    def test_model_save_load(self, spark, sample_features, temp_dir):
        """✅ Model can be saved and loaded."""
        from pyspark.ml.feature import VectorAssembler, StringIndexer
        from pyspark.ml.classification import RandomForestClassifier
        from pyspark.ml import Pipeline, PipelineModel

        df = spark.read.parquet(sample_features)

        feature_cols = ['amount', 'transaction_count', 'amount_per_transaction',
                        'time_delta_sec', 'velocity_per_sec']

        indexer = StringIndexer(inputCol="fraud_label", outputCol="label")
        assembler = VectorAssembler(inputCols=feature_cols, outputCol="features")
        rf = RandomForestClassifier(labelCol="label", featuresCol="features",
                                    numTrees=10, maxDepth=5, seed=42)

        pipeline = Pipeline(stages=[indexer, assembler, rf])
        model = pipeline.fit(df)

        # Save
        model_path = f"{temp_dir}/model"
        model.write().overwrite().save(model_path)

        # Load
        loaded_model = PipelineModel.load(model_path)

        assert loaded_model is not None
        print(f"✅ Model saved and loaded from {model_path}")