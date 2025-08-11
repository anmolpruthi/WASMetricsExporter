package com.score_me.was_metrics_exporter.entities;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class ProcessorNodeEntity {
    private final String id;
    private final String name;
    private final String type;
    private final List<String> outgoing = new ArrayList<>();
    private final List<String> incoming = new ArrayList<>();
    @Setter
    private int activeThreadCount = 0;

    public ProcessorNodeEntity(String id, String name, String type) {
        this.id = id;
        this.name = name != null ? name : "-";
        this.type = type != null ? type : "unknown";
    }
}
