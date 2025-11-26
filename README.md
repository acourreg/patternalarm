# patternalarm
Streaming fraud detection and anomaly alerting at scale. Kafka ingests 50K events/minute, Flink CEP detects pattern breaks, PostgreSQL tracks incidents. Designed for payment fraud, gaming anti-cheat, and IoT anomalies. Features sub-3s response times, chaos resilience testing, and automated spike handling. Production-grade streaming architecture.

## 🔧 Key Dependencies (Official Versions)

| Component | Version | Python Versions | Source |
|-----------|---------|-----------------|--------|
| Python | 3.8 | - | Default for this stack |
| Apache Airflow | 2.8.0 | 3.8, 3.9, 3.10, 3.11 | [Airflow 2.8 Prerequisites](https://airflow.apache.org/docs/apache-airflow/2.8.3/installation/prerequisites.html) |
| PySpark | 3.5.0 | 3.8, 3.9, 3.10, 3.11 | [PyPI pyspark 3.5.0](https://pypi.org/project/pyspark/3.5.0/) |
| Spark Provider | 4.1.5 | - | `apache-airflow-providers-apache-spark` |
| Java | 8, 11, or 17 | - | [Spark 3.5 Documentation](https://spark.apache.org/docs/3.5.6/) |

### ⚠️ Installation Instructions

```bash
# 1. Create virtual environment with Python 3.8 (officially supported by all components)
python3.8 -m venv venv
source venv/bin/activate

# 2. Install Airflow with constraints (REQUIRED)
AIRFLOW_VERSION=2.8.0
PYTHON_VERSION=3.8
CONSTRAINT_URL="https://raw.githubusercontent.com/apache/airflow/constraints-${AIRFLOW_VERSION}/constraints-${PYTHON_VERSION}.txt"

pip install "apache-airflow==${AIRFLOW_VERSION}" --constraint "${CONSTRAINT_URL}"

# 3. Install Spark provider and PySpark
pip install "apache-airflow-providers-apache-spark==4.1.5"
pip install "pyspark==3.5.0"
```

### 🚨 Known Issues
- **Spark provider 5.x requires Airflow 2.10+** - do not upgrade blindly
- **Java 24+ breaks Spark** - use Java 8, 11, or 17 only
- **Always use constraints file** when installing Airflow to avoid dependency conflicts

### 📚 Official Documentation References
- Airflow Prerequisites: https://airflow.apache.org/docs/apache-airflow/2.8.3/installation/prerequisites.html
- PySpark on PyPI: https://pypi.org/project/pyspark/3.5.0/
- Spark Documentation: https://spark.apache.org/docs/3.5.6/