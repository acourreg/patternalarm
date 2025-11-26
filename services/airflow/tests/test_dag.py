# services/airflow/tests/test_dag.py
import pytest
import sys
import os

# Add dags folder to path BEFORE any imports from it
DAGS_FOLDER = os.path.join(os.path.dirname(__file__), '..', 'dags')
sys.path.insert(0, DAGS_FOLDER)


class TestFraudTrainingDAG:
    """Tests for fraud_training_dag.py - no DB required"""

    def test_dag_imports(self):
        """✅ DAG file imports without errors."""
        from fraud_training_dag import dag
        assert dag is not None

    def test_dag_id(self):
        """✅ DAG has correct ID."""
        from fraud_training_dag import dag
        assert dag.dag_id == 'fraud_model_training'

    def test_dag_tasks(self):
        """✅ DAG has all expected tasks."""
        from fraud_training_dag import dag

        task_ids = [t.task_id for t in dag.tasks]
        assert 'extract_features' in task_ids
        assert 'train_model' in task_ids
        assert 'register_model' in task_ids
        assert 'validate_model' in task_ids

    def test_task_dependencies(self):
        """✅ Tasks have correct order."""
        from fraud_training_dag import dag

        extract = dag.get_task('extract_features')
        downstream_ids = [t.task_id for t in extract.downstream_list]
        assert 'train_model' in downstream_ids