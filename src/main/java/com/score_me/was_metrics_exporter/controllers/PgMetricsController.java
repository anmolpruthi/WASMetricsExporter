package com.score_me.was_metrics_exporter.controllers;

import com.score_me.was_metrics_exporter.entities.ProcessorNodeEntity;
import com.score_me.was_metrics_exporter.service.PgMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/pg-metrics")
public class PgMetricsController {

    private final PgMetricsService pgMetricsService;

    @PostMapping
    public ResponseEntity<?> getMetricsForPg(@RequestBody Map<String, String> body) {
        String pgId = body.get("processGroupId");
        if (pgId == null || pgId.isBlank()) {
            return ResponseEntity.badRequest().body("Missing processGroupId");
        }

        try {
            Map<String, ProcessorNodeEntity> metrics = pgMetricsService.getMetricsForProcessGroup(pgId);
//            return new ResponseEntity<>("The required metrics are as follows", )
        } catch (IOException e) {
            log.error("Error fetching metrics for PG {}: {}", pgId, e.getMessage());
            return ResponseEntity.internalServerError().body("Error fetching metrics");
        }
    }
}
