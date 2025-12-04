package com.patternalarm.dashboard.controller;

import com.patternalarm.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/health")
    @ResponseBody
    public ResponseEntity<java.util.Map<String, String>> health() {
        java.util.Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        return ResponseEntity.ok(status);
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("systemStatus", service.getSystemStatus());
        model.addAttribute("loadLevels", service.getLoadLevels());
        return "dashboard";
    }

    @PostMapping("/api/test/execute")
    @ResponseBody
    public java.util.Map<String, Object> executeTest(@RequestParam String domain, @RequestParam String loadLevel) {
        return service.executeTest(domain, loadLevel);
    }

    @GetMapping("/api/alerts")
    @ResponseBody
    public List<java.util.Map<String, Object>> getAlerts(@RequestParam(defaultValue = "10") int limit) {
        return service.getRecentAlerts(limit);
    }

    @GetMapping("/api/analytics/velocity")
    @ResponseBody
    public java.util.Map<String, Object> getVelocity() {
        return service.getVelocityAnalytics();
    }
}