package com.score_me.was_metrics_exporter.service;

import com.score_me.was_metrics_exporter.client.FlowApiClient;
import com.score_me.was_metrics_exporter.entities.ProcessGroupNodeEntity;
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

    public Map<String, Long> getMetricsForGroup(String groupId) throws IOException {
        Map<String, Long> metrics = new HashMap<>();

        Map<String, Long> metrics2 = methodHelper.getMetrics(flowApiClient, groupId);

//        Map<String, ProcessorNodeEntity> processorMap = methodHelper.buildProcessorMap(groupId);
//        Map<String, ProcessGroupNodeEntity> pgMap = methodHelper.buildProcessGroupMap(groupId);
//
//        if (processorMap.isEmpty() || pgMap.isEmpty()) {
//            return Collections.emptyMap();
//        }
//
//        metrics.put("processorCount", (long) processorMap.size());
//        metrics.put("maxPathDepth", (long) computeMaxDepth(pgMap));

        return metrics2;
    }

    private int computeMaxDepth(Map<String, ProcessGroupNodeEntity> pgMap) {
        Map<String, Integer> memo = new HashMap<>();
        int best = 0;
        for (String id : pgMap.keySet()) {
            best = Math.max(best, dfs(id, pgMap, new HashSet<>(), memo));
        }
        return best;
    }

    private int dfs(String pgId, Map<String, ProcessGroupNodeEntity> map, Set<String> visiting, Map<String, Integer> memo) {
        if (memo.containsKey(pgId)) return memo.get(pgId);
        if (!map.containsKey(pgId)) return 0;
        if (visiting.contains(pgId)) return 0;
        visiting.add(pgId);
        int best = 0;
        for (String child : map.get(pgId).getChildren()) {
            best = Math.max(best, 1 + dfs(child, map, visiting, memo));
        }
        visiting.remove(pgId);
        memo.put(pgId, best);
        return best;
    }
}
