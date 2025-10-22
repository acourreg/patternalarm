# ============================================================================
# PatternAlarm API Gateway - Dockerfile
# ============================================================================

FROM python:3.11-slim

WORKDIR /app

# Install system dependencies (for healthcheck)
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements and install
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY src/ ./src/
COPY main.py .

# Create non-root user for security
RUN useradd -m -u 1000 apiuser && \
    chown -R apiuser:apiuser /app

USER apiuser

# Expose port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

# Run FastAPI - use main:app since main.py imports from src.main
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]