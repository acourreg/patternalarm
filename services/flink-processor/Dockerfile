# ========================================
# Stage 1: Builder - Compile and Package
# ========================================
FROM amazoncorretto:17 AS builder

# Install wget and tar for SBT
RUN yum install -y wget tar gzip && \
    yum clean all

WORKDIR /build

# Copy SBT project files
COPY build.sbt .
COPY project ./project

# Download and install SBT
RUN wget https://github.com/sbt/sbt/releases/download/v1.9.7/sbt-1.9.7.tgz && \
    tar -xzf sbt-1.9.7.tgz && \
    rm sbt-1.9.7.tgz

# Download dependencies (cached layer)
RUN ./sbt/bin/sbt update

# Copy source code
COPY src ./src

# Compile and build fat JAR
RUN ./sbt/bin/sbt assembly

# ========================================
# Stage 2: Runtime - Lightweight Image
# ========================================
FROM amazoncorretto:17

WORKDIR /app

# Copy the fat JAR from builder stage
COPY --from=builder /build/target/scala-2.12/flink-processor-assembly-*.jar /app/flink-processor.jar

# Create non-root user for security (Amazon Linux style)
RUN yum install -y shadow-utils && \
    yum clean all && \
    groupadd -r flink && \
    useradd -r -g flink -d /app -s /sbin/nologin flink && \
    chown -R flink:flink /app

USER flink

# Expose Flink Web UI port (optional, for monitoring)
EXPOSE 8081

# Health check - verify JVM process is running
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
  CMD pgrep -f "flink-processor.jar" || exit 1

# JVM options and entry point
ENTRYPOINT ["java", \
  "--add-opens=java.base/java.util=ALL-UNNAMED", \
  "--add-opens=java.base/java.lang=ALL-UNNAMED", \
  "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED", \
  "--add-opens=java.base/java.io=ALL-UNNAMED", \
  "--add-opens=java.base/java.time=ALL-UNNAMED", \
  "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED", \
  "-Xms512m", \
  "-Xmx2g", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-jar", \
  "/app/flink-processor.jar"]