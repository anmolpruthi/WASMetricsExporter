package com.score_me.was_metrics_exporter.controllers;

import com.score_me.was_metrics_exporter.dto.BulkMetricsDTO;
import com.score_me.was_metrics_exporter.service.BulkExportMetrics;
import com.score_me.was_metrics_exporter.service.PgMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/pg-metrics")
public class PgMetricsController {

    private final PgMetricsService pgMetricsService;
    private final BulkExportMetrics bulkExportMetrics;

    @PostMapping
    public ResponseEntity<?> getMetricsForPg(@RequestBody Map<String, String> body) throws IOException {
        String groupId = body.get("processGroupId");
        if (groupId == null || groupId.isBlank()) {
            return ResponseEntity.badRequest().body("Missing processGroupId");
        }

        Map<String, Double> metrics = pgMetricsService.getMetricsForGroup(groupId);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/exportMetricsToTarget")
    public ResponseEntity<?> getAllMetricsForPg() throws IOException {

        String outputFilePath = bulkExportMetrics.exportMetricsBulk();
        log.info("Metrics exported successfully for group - {}", outputFilePath);
        return new ResponseEntity<>("Metrics exported successfully",HttpStatus.OK);
    }

    @PostMapping("/exportFileMetrics")
    public ResponseEntity<?>exportFileMetrics(@RequestParam("file") MultipartFile file ) throws IOException {
        try{
            InputStream inputStream = file.getInputStream();
            ByteArrayResource outputFile = bulkExportMetrics.exportMetricsToByteArray(inputStream);
            log.info("Metrics exported successfully for file - {}", file.getOriginalFilename());
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + file.getOriginalFilename() + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .contentLength(outputFile.contentLength())
                    .body(outputFile);
        }catch (Exception e) {
            log.error("Error exporting metrics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to export metrics: " + e.getMessage());
        }
    }
}

