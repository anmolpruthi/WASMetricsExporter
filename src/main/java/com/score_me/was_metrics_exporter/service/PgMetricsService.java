package com.score_me.was_metrics_exporter.service;

import com.score_me.was_metrics_exporter.client.FlowApiClient;
import com.score_me.was_metrics_exporter.helper.MethodHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;


@Service
public class PgMetricsService {

    private final MethodHelper methodHelper;
    private final FlowApiClient flowApiClient;


    @Autowired
    public PgMetricsService(MethodHelper methodHelper, FlowApiClient flowApiClient) {
        this.methodHelper = methodHelper;
        this.flowApiClient = flowApiClient;
    }

    public Map<String, Double> getMetricsForGroup(String groupId) throws IOException {
        return methodHelper.getMetrics(flowApiClient, groupId);
    }
}
