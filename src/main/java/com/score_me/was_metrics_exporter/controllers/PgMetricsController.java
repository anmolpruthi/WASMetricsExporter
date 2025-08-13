package com.score_me.was_metrics_exporter.controllers;

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
    public ResponseEntity<?> getMetricsForPg(@RequestBody Map<String, String> body) throws IOException {
        String groupId = body.get("processGroupId");
        if (groupId == null || groupId.isBlank()) {
            return ResponseEntity.badRequest().body("Missing processGroupId");
        }

        Map<String, Double> metrics = pgMetricsService.getMetricsForGroup(groupId);
        return ResponseEntity.ok(metrics);
    }
}

