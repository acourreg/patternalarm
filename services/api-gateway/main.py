"""
FastAPI API Gateway
- Model serving for Flink (POST /predict) - MOCKED
- Query layer for Dashboard (GET /alerts, /analytics) - REAL DATABASE
"""
import os
os.environ['JAVA_HOME'] = '/opt/homebrew/opt/openjdk@17'

from fastapi import FastAPI

from src.routes.alerts_router import alerts_router
from src.routes.health_router import health_router
from src.routes.predict_router import predict_router
from src.routes.analytics_router import analytics_router

app = FastAPI(
    title="PatternAlarm API Gateway",
    description="ML serving + Real PostgreSQL Query layer",
    version="1.0.0"
)

# ✅ Include all routers
app.include_router(alerts_router)
app.include_router(analytics_router)
app.include_router(health_router)
app.include_router(predict_router)


@app.get("/")
async def root():
    """API info"""
    return {
        "service": "PatternAlarm API Gateway",
        "version": "1.0.0",
        "database": "✅ PostgreSQL (Real queries)",
        "endpoints": {
            "model_serving": "POST /predict (mocked)",
            "health": "GET /health",
            "alerts": "GET /alerts (✅ REAL DB)",
            "alert_detail": "GET /alerts/{id} (✅ REAL DB)",
            "analytics_summary": "GET /analytics/summary (✅ REAL DB)",
            "analytics_domain": "GET /analytics/domain/{domain} (✅ REAL DB)"
        }
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8080)