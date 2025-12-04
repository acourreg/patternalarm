package com.patternalarm.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;

import java.time.Instant;
import java.util.*;

@Service
@SuppressWarnings("unchecked")
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    @Value("${ENVIRONMENT:local}")
    private String environment;

    private boolean isMockMode() {
        // Real mode if ENVIRONMENT is "dev" or "prod"
        return !("dev".equalsIgnoreCase(environment) || "prod".equalsIgnoreCase(environment));
    }

    @Value("${patternalarm.api-gateway.url:http://api-gateway.patternalarm.local:8080}")
    private String apiGatewayUrl;

    @Value("${patternalarm.lambda.function-name:patternalarm-event-generator}")
    private String lambdaFunctionName;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();
    private LambdaClient lambdaClient;

    // ========== ALERTS ==========

    public List<java.util.Map<String, Object>> getRecentAlerts(int limit) {
        if (isMockMode()) return mockAlerts(limit);

        try {
            String json = restTemplate.getForObject(apiGatewayUrl + "/alerts?limit=" + limit, String.class);
            java.util.Map<String, Object> response = mapper.readValue(json, java.util.Map.class);
            return (List<java.util.Map<String, Object>>) response.getOrDefault("alerts", Collections.emptyList());
        } catch (Exception e) {
            log.warn("API Gateway unreachable, falling back to mock: {}", e.getMessage());
            return mockAlerts(limit);
        }
    }

    // ========== VELOCITY ANALYTICS ==========

    public java.util.Map<String, Object> getVelocityAnalytics() {
        if (isMockMode()) return mockVelocity();

        try {
            String json = restTemplate.getForObject(apiGatewayUrl + "/analytics/velocity", String.class);
            return mapper.readValue(json, java.util.Map.class);
        } catch (Exception e) {
            log.warn("Velocity API unreachable: {}", e.getMessage());
            return mockVelocity();
        }
    }

    // ========== TEST EXECUTION ==========

    public java.util.Map<String, Object> executeTest(String domain, String loadLevel) {
        String testId = "test-" + UUID.randomUUID().toString().substring(0, 8);

        if (isMockMode()) {
            log.info("MOCK: Test {} started for {} @ {}", testId, domain, loadLevel);
            java.util.Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("testId", testId);
            result.put("message", "Mock test started");
            return result;
        }

        try {
            if (lambdaClient == null) lambdaClient = LambdaClient.builder().build();

            java.util.Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("domain", domain);
            payloadMap.put("test_id", testId);
            payloadMap.put("load_level", loadLevel);
            String payload = mapper.writeValueAsString(payloadMap);

            lambdaClient.invoke(InvokeRequest.builder()
                    .functionName(lambdaFunctionName)
                    .payload(SdkBytes.fromUtf8String(payload))
                    .invocationType("Event")
                    .build());

            log.info("Lambda invoked: {} for {} @ {}", testId, domain, loadLevel);
            java.util.Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("testId", testId);
            result.put("message", "Lambda invoked");
            return result;

        } catch (Exception e) {
            log.error("Lambda invocation failed: {}", e.getMessage());
            java.util.Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    // ========== SYSTEM STATUS ==========

    public java.util.Map<String, Object> getSystemStatus() {
        java.util.Map<String, Object> status = new HashMap<>();

        if (isMockMode()) {
            java.util.Map<String, String> mockStatus = new HashMap<>();
            mockStatus.put("status", "mock");
            mockStatus.put("message", "Mock mode enabled");
            status.put("api-gateway", mockStatus);
            status.put("kafka", mockStatus);
            status.put("flink", mockStatus);
            return status;
        }

        try {
            restTemplate.getForObject(apiGatewayUrl + "/health", String.class);
            java.util.Map<String, String> apiStatus = new HashMap<>();
            apiStatus.put("status", "connected");
            apiStatus.put("message", "API Gateway healthy");
            status.put("api-gateway", apiStatus);

            java.util.Map<String, String> kafkaStatus = new HashMap<>();
            kafkaStatus.put("status", "connected");
            kafkaStatus.put("message", "Via API Gateway");
            status.put("kafka", kafkaStatus);

            java.util.Map<String, String> flinkStatus = new HashMap<>();
            flinkStatus.put("status", "running");
            flinkStatus.put("message", "Via API Gateway");
            status.put("flink", flinkStatus);
        } catch (Exception e) {
            java.util.Map<String, String> errorStatus = new HashMap<>();
            errorStatus.put("status", "error");
            errorStatus.put("message", e.getMessage());
            status.put("api-gateway", errorStatus);

            java.util.Map<String, String> unknownStatus = new HashMap<>();
            unknownStatus.put("status", "unknown");
            unknownStatus.put("message", "API Gateway down");
            status.put("kafka", unknownStatus);
            status.put("flink", unknownStatus);
        }
        return status;
    }

    // ========== LOAD LEVELS ==========

    public List<java.util.Map<String, Object>> getLoadLevels() {
        List<java.util.Map<String, Object>> levels = new ArrayList<>();

        java.util.Map<String, Object> mini = new HashMap<>();
        mini.put("value", "mini");
        mini.put("label", "Mini (Test)");
        mini.put("eventsPerMin", "5/min");
        mini.put("cost", "$0.01");
        levels.add(mini);

        java.util.Map<String, Object> normal = new HashMap<>();
        normal.put("value", "normal");
        normal.put("label", "Normal");
        normal.put("eventsPerMin", "10K/min");
        normal.put("cost", "$0.15");
        levels.add(normal);

        java.util.Map<String, Object> peak = new HashMap<>();
        peak.put("value", "peak");
        peak.put("label", "Peak");
        peak.put("eventsPerMin", "50K/min");
        peak.put("cost", "$1.25");
        levels.add(peak);

        java.util.Map<String, Object> crisis = new HashMap<>();
        crisis.put("value", "crisis");
        crisis.put("label", "Crisis");
        crisis.put("eventsPerMin", "100K/min");
        crisis.put("cost", "$3.80");
        levels.add(crisis);

        return levels;
    }

    // ========== MOCK DATA ==========

    private List<java.util.Map<String, Object>> mockAlerts(int limit) {
        List<java.util.Map<String, Object>> alerts = new ArrayList<>();
        String[] domains = {"gaming", "fintech", "ecommerce"};
        String[] types = {"velocity_spike", "amount_anomaly", "account_takeover"};
        String[] severities = {"low", "medium", "high", "critical"};

        for (int i = 0; i < limit; i++) {
            java.util.Map<String, Object> alert = new HashMap<>();
            alert.put("alertId", 1000 + i);
            alert.put("domain", domains[i % 3]);
            alert.put("actorId", "ACTOR_" + (100 + i));
            alert.put("alertType", types[i % 3]);
            alert.put("severity", severities[i % 4]);
            alert.put("fraudScore", 50 + (i * 5) % 50);
            alert.put("totalAmount", 100.0 + (i * 50));
            alert.put("lastSeen", Instant.now().minusSeconds(i * 10).toString());
            alerts.add(alert);
        }
        return alerts;
    }

    private java.util.Map<String, Object> mockVelocity() {
        List<java.util.Map<String, Object>> points = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            java.util.Map<String, Object> point = new HashMap<>();
            point.put("bucket", Instant.now().minusSeconds((12 - i) * 5).toString());
            point.put("domain", "gaming");
            point.put("y1_velocity", 5 + (int)(Math.random() * 10));
            point.put("y2_avg_amount", 150.0 + Math.random() * 200);
            points.add(point);
        }

        java.util.Map<String, Object> result = new HashMap<>();
        result.put("data_points", points);
        result.put("total_alerts", 60);
        return result;
    }
}