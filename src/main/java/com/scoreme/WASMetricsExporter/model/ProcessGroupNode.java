package com.scoreme.WASMetricsExporter.model;

import lombok.Data;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProcessGroupNode {
    private final String id;
    private final String name;
    private final List<String> children = new ArrayList<>();
}
