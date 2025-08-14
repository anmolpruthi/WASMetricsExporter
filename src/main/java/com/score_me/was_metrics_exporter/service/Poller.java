package com.score_me.was_metrics_exporter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


/**
 * Poller class that periodically refreshes metrics data.
 * It uses the {@link MetricsService} to fetch and update metrics.
 * The polling interval can be configured via the 'monitor.poll-interval-ms' property.
 */
@Component
@EnableScheduling
public class Poller {
    private final MetricsService metricsService;
    private final Logger log = LoggerFactory.getLogger(Poller.class);

    public Poller(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Scheduled(fixedDelayString = "${monitor.poll-interval-ms:1000}")
    public void run() {
        try {
            metricsService.refresh();
        } catch (Exception e) {
            log.error("Polling error: {}", e.getMessage(), e);
        }
    }
}