package com.patternalarm.dashboard.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dashboard service with mocked data
 * TODO: Replace with real AWS/Kafka/PostgreSQL integration
 */
@Service
public class DashboardService {

    // In-memory storage for demo
    private final Map<String, Map<String, Object>> runningTests = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> testHistory = new ArrayList<>();

    /**
     * AC1.1: Get system status (mocked)
     */
    public Map<String, Object> getSystemStatus() {
        return Map.of(
                "kafka", Map.of("status", "connected", "message", "Mock: 3 brokers available"),
                "flink", Map.of("status", "running", "message", "Mock: 2 tasks running"),
                "database", Map.of("status", "accessible", "message", "Mock: PostgreSQL ready")
        );
    }

    /**
     * AC1.1: Get last 10 test runs (mocked)
     */
    public List<Map<String, Object>> getRecentTestRuns() {
        // Return mock data if history is empty
        if (testHistory.isEmpty()) {
            return List.of(
                    Map.of(
                            "id", "test-001",
                            "domain", "gaming",
                            "loadLevel", "normal",
                            "status", "completed",
                            "eventsProcessed", 10000,
                            "fraudDetected", 234,
                            "fraudRate", 2.34,
                            "startedAt", "2025-10-07T10:30:00Z",
                            "cost", "$0.15"
                    ),
                    Map.of(
                            "id", "test-002",
                            "domain", "ecommerce",
                            "loadLevel", "peak",
                            "status", "completed",
                            "eventsProcessed", 50000,
                            "fraudDetected", 1200,
                            "fraudRate", 2.40,
                            "startedAt", "2025-10-07T11:15:00Z",
                            "cost", "$1.25"
                    )
            );
        }
        return testHistory.subList(0, Math.min(10, testHistory.size()));
    }

    /**
     * AC1.3: Get load level configurations
     */
    public List<Map<String, Object>> getLoadLevels() {
        return List.of(
                Map.of(
                        "value", "normal",
                        "label", "Normal Load",
                        "eventsPerMin", "10K/min",
                        "cost", "$0.15",
                        "warning", false
                ),
                Map.of(
                        "value", "peak",
                        "label", "Peak Load",
                        "eventsPerMin", "50K/min",
                        "cost", "$1.25",
                        "warning", false
                ),
                Map.of(
                        "value", "crisis",
                        "label", "Crisis Load",
                        "eventsPerMin", "100K/min",
                        "cost", "$3.80",
                        "warning", true
                )
        );
    }

    /**
     * AC1.2: Execute test (mocked)
     * TODO: Replace with real Lambda invocation
     */
    public Map<String, Object> executeTest(String domain, String loadLevel) {
        String testId = "test-" + UUID.randomUUID().toString().substring(0, 8);

        // Simulate test execution
        Map<String, Object> testRun = new HashMap<>();
        testRun.put("testId", testId);
        testRun.put("domain", domain);
        testRun.put("loadLevel", loadLevel);
        testRun.put("status", "running");
        testRun.put("startedAt", Instant.now().toString());
        testRun.put("eventsProcessed", 0);
        testRun.put("fraudDetected", 0);
        testRun.put("currentThroughput", 0.0);

        runningTests.put(testId, testRun);

        // Simulate completion after 10 seconds
        simulateTestExecution(testId, domain, loadLevel);

        return Map.of(
                "success", true,
                "testId", testId,
                "message", "Test execution started",
                "estimatedDuration", "60 seconds"
        );
    }

    /**
     * AC1.4: Get test progress (mocked)
     */
    public Map<String, Object> getTestProgress(String testId) {
        Map<String, Object> test = runningTests.get(testId);

        if (test == null) {
            return Map.of("error", "Test not found");
        }

        return test;
    }

    /**
     * AC1.5: Get test results (mocked)
     */
    public Map<String, Object> getTestResults(String testId) {
        return Map.of(
                "testId", testId,
                "domain", "gaming",
                "loadLevel", "normal",
                "summary", Map.of(
                        "totalEvents", 10000,
                        "fraudDetected", 234,
                        "fraudRate", 2.34,
                        "falsePositives", 12,
                        "falsePositiveRate", 5.13,
                        "throughput", 166.67
                ),
                "latency", Map.of(
                        "p50", 45,
                        "p90", 89,
                        "p99", 156
                ),
                "topPatterns", List.of(
                        Map.of("pattern", "Velocity abuse", "count", 89, "confidence", 0.92),
                        Map.of("pattern", "Card testing", "count", 67, "confidence", 0.88),
                        Map.of("pattern", "Account takeover", "count", 45, "confidence", 0.85)
                ),
                "costs", Map.of(
                        "lambda", "$0.0001",
                        "kafka", "$0.05",
                        "flink", "$0.07",
                        "total", "$0.15"
                )
        );
    }

    /**
     * Simulate test execution with progress updates
     */
    private void simulateTestExecution(String testId, String domain, String loadLevel) {
        new Thread(() -> {
            try {
                Map<String, Object> test = runningTests.get(testId);
                int totalEvents = loadLevel.equals("crisis") ? 100000 :
                        loadLevel.equals("peak") ? 50000 : 10000;

                // Simulate progress over 10 seconds
                for (int i = 1; i <= 10; i++) {
                    Thread.sleep(1000);
                    int processed = (totalEvents / 10) * i;
                    int frauds = (int) (processed * 0.0234); // 2.34% fraud rate

                    test.put("eventsProcessed", processed);
                    test.put("fraudDetected", frauds);
                    test.put("currentThroughput", totalEvents / 60.0);
                }

                // Mark as completed
                test.put("status", "completed");
                test.put("completedAt", Instant.now().toString());

                // Add to history
                testHistory.add(0, new HashMap<>(test));

                // Remove from running
                runningTests.remove(testId);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}