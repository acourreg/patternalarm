# Stage 1: Build
FROM public.ecr.aws/docker/library/gradle:8.5-jdk17-alpine AS builder
WORKDIR /build
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
COPY src src
RUN gradle bootJar --no-daemon

# Stage 2: Runtime
FROM public.ecr.aws/amazoncorretto/amazoncorretto:17-alpine
WORKDIR /app
COPY --from=builder /build/build/libs/*.jar app.jar

# Health check endpoint
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD wget -q --spider http://localhost:8082/health || exit 1

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]