package com.patternalarm.dashboard.controller;

import com.patternalarm.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Health check endpoint for ALB
     */
    @GetMapping("/health")
    @ResponseBody
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "dashboard"
        ));
    }

    /**
     * AC1.1: Main Dashboard Page
     * Display 3 test scenario buttons, system status, last 10 test runs
     */
    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("systemStatus", dashboardService.getSystemStatus());
        model.addAttribute("recentRuns", dashboardService.getRecentTestRuns());
        model.addAttribute("loadLevels", dashboardService.getLoadLevels());
        return "dashboard";
    }

    /**
     * AC1.2: Test Execution Control
     * Trigger test execution (mocked for now)
     */
    @PostMapping("/api/test/execute")
    @ResponseBody
    public Map<String, Object> executeTest(
            @RequestParam String domain,
            @RequestParam String loadLevel) {
        return dashboardService.executeTest(domain, loadLevel);
    }

    /**
     * AC1.4: Real-Time Progress Monitoring
     * Poll every 3 seconds for test progress
     */
    @GetMapping("/api/test/{testId}/progress")
    @ResponseBody
    public Map<String, Object> getProgress(@PathVariable String testId) {
        return dashboardService.getTestProgress(testId);
    }

    /**
     * AC1.5: Results Dashboard
     * Display test results after completion
     */
    @GetMapping("/results/{testId}")
    public String showResults(@PathVariable String testId, Model model) {
        model.addAttribute("results", dashboardService.getTestResults(testId));
        return "results";
    }
}