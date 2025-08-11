package com.score_me.was_metrics_exporter.service;

import com.score_me.was_metrics_exporter.entities.ProcessorNodeEntity;
import com.score_me.was_metrics_exporter.model.GraphBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Component
@Service
@RequiredArgsConstructor
public class PgMetricsService {

    private final GraphBuilder graphBuilder;

    public Map<String, ProcessorNodeEntity> getMetricsForProcessGroup(String pgId) throws IOException {
        return graphBuilder.buildProcessorMapForPg(pgId);
    }

}
