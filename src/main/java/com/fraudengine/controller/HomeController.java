package com.fraudengine.controller;

import com.fraudengine.service.AlertService;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root endpoint — returns a welcome message and API directory
 * so that hitting localhost:8081 gives useful info instead of a 500 error.
 */
@RestController
public class HomeController {

    private final AlertService alertService;

    public HomeController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/")
    public Map<String, Object> home() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("application", "Real-Time Fraud Detection Engine");
        response.put("status", "RUNNING");
        response.put("timestamp", Instant.now().toString());

        long totalAlerts = alertService.getAlerts(PageRequest.of(0, 1)).getTotalElements();
        response.put("totalFraudAlerts", totalAlerts);

        Map<String, String> endpoints = new LinkedHashMap<>();
        endpoints.put("POST /api/transactions", "Submit a transaction for fraud evaluation");
        endpoints.put("POST /api/transactions/simulate?count=N", "Generate N demo transactions");
        endpoints.put("GET /api/alerts", "List fraud alerts (supports ?accountId=, ?status=, ?page=, ?size=)");
        endpoints.put("PATCH /api/alerts/{id}/status", "Update alert status (body: {\"status\": \"REVIEWED\"})");
        endpoints.put("GET /actuator/health", "Application health check");
        response.put("endpoints", endpoints);

        return response;
    }
}
