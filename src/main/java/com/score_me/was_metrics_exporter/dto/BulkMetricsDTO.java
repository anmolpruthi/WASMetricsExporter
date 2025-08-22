package com.score_me.was_metrics_exporter.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class BulkMetricsDTO {
    private String fileLocation;
    private String fileName;
}
