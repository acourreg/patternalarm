# services/api-gateway/main.py

"""
FastAPI API Gateway
- Model serving for Flink (POST /predict/batch) - REAL SPARK ML (97.5% accuracy)
- Query layer for Dashboard (GET /alerts, /analytics) - REAL DATABASE
"""
import os
os.environ['JAVA_HOME'] = os.getenv('JAVA_HOME', '/usr/lib/jvm/java-21-openjdk-amd64')

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from src.routes.alerts_router import alerts_router
from src.routes.health_router import health_router
from src.routes.predict_router import predict_router
from src.routes.analytics_router import analytics_router

app = FastAPI(
    title="PatternAlarm API Gateway",
    description="Spark ML Fraud Detection + Real PostgreSQL Query Layer",
    version="1.0.0"
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ✅ Include all routers
app.include_router(health_router)
app.include_router(predict_router)
app.include_router(alerts_router)
app.include_router(analytics_router)

@app.get("/")
async def root():
    """API info"""
    return {
        "service": "PatternAlarm API Gateway",
        "version": "1.0.0",
        "ml_model": "✅ Spark ML (97.5% accuracy, 8 classes)",
        "database": "✅ PostgreSQL (Real queries)",
        "redis": "✅ Caching layer",
        "endpoints": {
            "model_serving": "POST /predict/batch (✅ REAL SPARK ML)",
            "model_health": "GET /predict/health",
            "health": "GET /health",
            "alerts": "GET /alerts (✅ REAL DB)",
            "alert_detail": "GET /alerts/{id} (✅ REAL DB)",
            "analytics_summary": "GET /analytics/summary (✅ REAL DB + Redis cache)",
            "analytics_domain": "GET /analytics/domain/{domain} (✅ REAL DB + Redis cache)"
        }
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)