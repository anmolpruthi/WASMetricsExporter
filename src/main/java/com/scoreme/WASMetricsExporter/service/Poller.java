package com.scoreme.WASMetricsExporter.service;

import com.scoreme.WASMetricsExporter.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class Poller {
    private final MetricsService metricsService;
    private final Logger log = LoggerFactory.getLogger(Poller.class);

    public Poller(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Scheduled(fixedDelayString = "${monitor.poll-interval-ms:30000}")
    public void run() {
        try {
            metricsService.refresh();
        } catch (Exception e) {
            log.error("Polling error: {}", e.getMessage(), e);
        }
    }
}