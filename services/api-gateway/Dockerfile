# ============================================================================
# PatternAlarm API Gateway - Dockerfile
# ============================================================================

FROM public.ecr.aws/docker/library/python:3.11-slim

WORKDIR /app

# Install system dependencies (for healthcheck)
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    git \
    procps \
    openjdk-21-jre-headless \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

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

# Default port (can be overridden by env var)
ENV PORT=8080
EXPOSE ${PORT}

# Health check (uses PORT env var)
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:${PORT}/health || exit 1

# Run FastAPI on configurable port
CMD uvicorn main:app --host 0.0.0.0 --port ${PORT}