package com.score_me.was_metrics_exporter.entities;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProcessGroupNodeEntity {
    private final String id;
    private final String name;
    private final List<String> children = new ArrayList<>();
}
