package com.fraudengine.controller;

import com.fraudengine.model.FraudAlert;
import com.fraudengine.service.AlertService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST API for querying and managing fraud alerts.
 *
 * Endpoints:
 *   GET   /api/alerts             — List alerts (paginated, filterable)
 *   PATCH /api/alerts/{id}/status — Update alert status (analyst workflow)
 *
 * This API simulates the interface a fraud analyst would use:
 * - View open alerts sorted by recency
 * - Filter by account or status
 * - Review and dismiss alerts
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * List fraud alerts with optional filters.
     *
     * Examples:
     *   GET /api/alerts                           — all alerts, page 0
     *   GET /api/alerts?accountId=ACC-001          — filter by account
     *   GET /api/alerts?status=OPEN&page=0&size=10 — filter by status
     */
    @GetMapping
    public Page<FraudAlert> getAlerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) FraudAlert.AlertStatus status) {

        Pageable pageable = PageRequest.of(page, size);

        if (accountId != null && !accountId.isBlank()) {
            return alertService.getAlertsByAccount(accountId, pageable);
        }
        if (status != null) {
            return alertService.getAlertsByStatus(status, pageable);
        }
        return alertService.getAlerts(pageable);
    }

    /**
     * Update the status of an alert.
     *
     * Models the analyst workflow: OPEN → REVIEWED → DISMISSED
     *
     * Example:
     *   PATCH /api/alerts/42/status
     *   Body: { "status": "REVIEWED" }
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<FraudAlert> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String statusValue = body.get("status");
        if (statusValue == null || statusValue.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        FraudAlert.AlertStatus newStatus = FraudAlert.AlertStatus.valueOf(
                statusValue.toUpperCase());
        FraudAlert updated = alertService.updateStatus(id, newStatus);
        return ResponseEntity.ok(updated);
    }
}
