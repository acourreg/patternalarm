# PatternAlarm
Streaming fraud detection and anomaly alerting at scale. Kafka ingests 50K events/minute, Flink CEP detects pattern breaks, PostgreSQL tracks incidents. Designed for payment fraud, gaming anti-cheat, and IoT anomalies. Features sub-3s response times, chaos resilience testing, and automated spike handling. Production-grade streaming architecture.

## 🏗️ Architecture

```
┌─────────────┐    ┌─────────────┐    ┌─────────────────┐    ┌─────────────┐
│   Lambda    │───▶│    MSK      │───▶│  Flink (ECS)    │───▶│     RDS     │
│ (Generator) │    │   (Kafka)   │    │  + Spark ML     │    │ (PostgreSQL)│
└─────────────┘    └─────────────┘    └────────┬────────┘    └─────────────┘
                                               │
                                               ▼
                                      ┌─────────────────┐
                                      │  API Gateway    │
                                      │  (FastAPI +     │
                                      │   Spark ML)     │
                                      └────────┬────────┘
                                               │
                                               ▼
                                      ┌─────────────────┐
                                      │   Dashboard     │
                                      │    (React)      │
                                      └─────────────────┘
```

## 🔬 Performance Investigation

### Context
During load testing, we observed that the ML prediction API was significantly slower than expected, causing timeouts when Flink consumed backlogged messages from MSK.

### Methodology
1. **Isolated API Gateway** - Scaled down Flink to prevent flood during tests
2. **Direct curl tests** - From inside API Gateway container to eliminate network latency
3. **Sequential single requests** - Measure individual prediction latency
4. **Batch requests** - Compare `/predict` vs `/predict/batch` endpoints

### Measurements

#### Single Predictions (`/predict`)

| Request # | Inference Time | Notes |
|-----------|----------------|-------|
| 1 | 1913ms | Cold start |
| 2 | 1210ms | Warmup |
| 3 | 1201ms | Warmup |
| 4 | 974ms | Stable |
| 5 | 950ms | Stable |

**Observation:** ~950ms per prediction at steady state.

#### Batch Predictions (`/predict/batch`)

| Batch Size | Total Time | Per Prediction | Speedup |
|------------|------------|----------------|---------|
| 1 | 950ms | 950ms | 1x |
| 10 | 783ms | 78ms | **12x** |
| 50 | 914ms | 18ms | **53x** |

**Key Insight:** Batch processing is dramatically more efficient due to fixed overhead in Spark job setup.

### Analysis

The ~900ms overhead is **fixed cost** regardless of batch size:
- `createDataFrame()` - Python → JVM serialization
- `model.transform()` - Spark job initialization
- `.collect()` - Results back to Python driver

The actual ML inference is near-instantaneous once the job runs.

### Capacity Estimates

| Configuration | Predictions/min | Supported Mode |
|---------------|-----------------|----------------|
| 1 worker, single requests | 63 | ❌ |
| 1 worker, batch 10 | 770 | MINI ✅ |
| 1 worker, batch 50 | 2,500 | MINI ✅, ~25% NORMAL |
| 1 worker, batch 100 | ~3,700 | MINI ✅, ~37% NORMAL |
| 4 workers, batch 100 | ~15,000 | NORMAL ✅ |

Target modes:
- **MINI:** 5 events/min (demo)
- **NORMAL:** 10,000 events/min (production)
- **PEAK:** 50,000 events/min (stress)
- **CRISIS:** 100,000 events/min (chaos)

### Next Steps
1. **Add timing instrumentation** to `predict_batch()` to identify exact bottleneck
2. **Modify Flink** to use batch endpoint instead of individual requests
3. **Tune batch size** based on latency requirements vs throughput

### Profiling Code (WIP)

```python
async def predict_batch(self, request: BatchPredictRequest) -> BatchPredictResponse:
    start = time.time()

    actors = [pred_req.to_actor_transactions() for pred_req in request.predictions]
    t1 = time.time()
    print(f"⏱️ [1] to_actor_transactions: {(t1-start)*1000:.0f}ms")

    all_features = [FeatureEngineering.extract_features_pandas(actor) for actor in actors]
    t2 = time.time()
    print(f"⏱️ [2] extract_features_pandas: {(t2-t1)*1000:.0f}ms")

    # ... (metadata extraction)
    t3 = time.time()
    print(f"⏱️ [3] pop metadata: {(t3-t2)*1000:.0f}ms")

    df = pd.DataFrame(all_features)
    t4 = time.time()
    print(f"⏱️ [4] pd.DataFrame: {(t4-t3)*1000:.0f}ms")

    spark_df = self._spark.createDataFrame(df)
    t5 = time.time()
    print(f"⏱️ [5] createDataFrame: {(t5-t4)*1000:.0f}ms")

    predictions_df = self._model.transform(spark_df)
    t6 = time.time()
    print(f"⏱️ [6] model.transform: {(t6-t5)*1000:.0f}ms")

    results = predictions_df.select("prediction", "probability").collect()
    t7 = time.time()
    print(f"⏱️ [7] collect: {(t7-t6)*1000:.0f}ms")

    # ... (format responses)
    t8 = time.time()
    print(f"⏱️ [8] format responses: {(t8-t7)*1000:.0f}ms")
```

### Fix: Flink Async → Sync Batch

Le bottleneck `.collect()` (81% du temps) est un coût fixe par job Spark. Solution: batcher 100 requêtes pour amortir ce coût.

**Avant:** Async parallèle (étranglait Spark)
```scala
val scoredStream = AsyncDataStream.unorderedWait(aggregates, fraudScoringAsyncFunction, ...)
```

**Après:** Sync batch 100 → `/predict/batch`
```scala
val scoredStream = aggregates.process(new FraudScoringBatchFunction(Config.FastApi.url, batchSize = 100, flushIntervalMs = 5000))
```


---

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
- **Flink → API Gateway timeouts** - Under MSK backlog pressure, individual `/predict` calls timeout. Solution: use `/predict/batch` with batching in Flink.

### 📚 Official Documentation References
- Airflow Prerequisites: https://airflow.apache.org/docs/apache-airflow/2.8.3/installation/prerequisites.html
- PySpark on PyPI: https://pypi.org/project/pyspark/3.5.0/
- Spark Documentation: https://spark.apache.org/docs/3.5.6/

