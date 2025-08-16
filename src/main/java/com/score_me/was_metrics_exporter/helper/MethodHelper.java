package com.score_me.was_metrics_exporter.helper;

import com.fasterxml.jackson.databind.JsonNode;
import com.score_me.was_metrics_exporter.client.FlowApiClient;
import com.score_me.was_metrics_exporter.entities.ProcessGroupNodeEntity;
import com.score_me.was_metrics_exporter.entities.ProcessorNodeEntity;
import com.score_me.was_metrics_exporter.utils.GraphBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
@Slf4j
public class MethodHelper{

    private final FlowApiClient client;
    private final GraphBuilder graphBuilder;
    private static final String PG_ENDPOINT = "/process-groups/";


    public MethodHelper(FlowApiClient client, GraphBuilder graphBuilder) {
        this.client = client;
        this.graphBuilder = graphBuilder;
    }

    /**
     * Method to get metrics for a process group.
     * This method will return a map of metrics for the specified process group.
     * @param flowApiClient FlowApiClient instance
     * @param groupId ID of the process group
     * @return Map of metric names to their values
     * @throws IOException if an error occurs during processing
     */
    public Map<String, Double> getMetrics(FlowApiClient flowApiClient, String groupId) throws IOException {
        Map<String, Double> metrics = new HashMap<>();
        Map<String, ProcessorNodeEntity> processorMap = graphBuilder.buildProcessorMap(groupId);
        Map<String, ProcessGroupNodeEntity> processGroupMap = graphBuilder.buildProcessGroupMap(groupId);
        if (processorMap == null || processorMap.isEmpty()) {
            log.warn("Error creating processor map");
            return Collections.emptyMap();
        } else if (processGroupMap == null || processGroupMap.isEmpty()) {
            log.warn("Error creating process group map");
            return Collections.emptyMap();
        }

        double processorCount = processorMap.size();
        metrics.put("processorCount", processorCount);

        double maxPathDepth = computeMaxDepth(processGroupMap);
        metrics.put("maxPathDepth", maxPathDepth);

        double avgF = processorMap.values().stream().mapToInt(n -> n.getOutgoing().size()).average().orElse(0.0);
        metrics.put("avgF", avgF);

        double qbpPctVal =computeBackPressurePercent(flowApiClient);
        metrics.put("qbpPctVal", qbpPctVal);

        double ipd = processorMap.values().stream().filter(n -> (n.getIncoming().size() + n.getOutgoing().size()) > 2).count();
        metrics.put("ipd", ipd);

        double threads = processorMap.values().stream().mapToInt(ProcessorNodeEntity::getActiveThreadCount).sum();
        metrics.put("activeThreads", threads);

        double scripted = processorMap.values().stream().filter(n -> isScriptedType(n.getType()) || containsEL(n.getName())).count();
        double scriptedPctVal = 100.0 * scripted / Math.max(processorCount, 1);
        metrics.put("scriptedPctVal", scriptedPctVal);

        double inputPortCount = graphBuilder.getPortCount("input", groupId);
        metrics.put("inputPortCount", inputPortCount);

        double outputPortCount = graphBuilder.getPortCount("output", groupId);
        metrics.put("outputPortCount", outputPortCount);

        double processorCountActual = processorCount - inputPortCount - outputPortCount;
        metrics.put("processorCountFinal", processorCountActual);

        return metrics;

    }



    protected static boolean isScriptedType(String type) {
        if (type == null) return false;
        String t = type.toLowerCase();
        return t.contains("executescript") || t.contains("script") || t.contains("invokescript") || t.contains("scripted");
    }

    protected static boolean containsEL(String text) {
        if (text == null) return false;
        return text.contains("${") || text.contains("#{");
    }

    protected static int computeMaxDepth(Map<String, ProcessGroupNodeEntity> pgMap) {
        Map<String, Integer> memo = new HashMap<>();
        int best = 0;
        for (String id : pgMap.keySet()) {
            best = Math.max(best, dfsPGDepth(id, pgMap, new HashSet<>(), memo));
        }
        return best;
    }

    protected static int dfsPGDepth(String pgId, Map<String, ProcessGroupNodeEntity> map, Set<String> visiting, Map<String, Integer> memo) {
        if (memo.containsKey(pgId)) return memo.get(pgId);
        if (!map.containsKey(pgId)) return 0;
        if (visiting.contains(pgId)) return 0;

        visiting.add(pgId);
        int best = 0;
        for (String child : map.get(pgId).getChildren()) {
            best = Math.max(best, 1 + dfsPGDepth(child, map, visiting, memo));
        }
        visiting.remove(pgId);

        memo.put(pgId, best);
        return best;
    }

    private static double computeBackPressurePercent(FlowApiClient client) {
        try {
            JsonNode top = client.get("/flow/status");
            if (top == null) return 0.0;
            List<JsonNode> conns = new ArrayList<>();
            if (top.has("connectionStatus")) {
                top.get("connectionStatus").forEach(conns::add);
            }
            if (conns.isEmpty()) {
                JsonNode rootConns = client.get("/process-groups/root/connections");
                if (rootConns != null && rootConns.has("connections")) {
                    rootConns.get("connections").forEach(conns::add);
                }
            }
            int total = conns.size();
            int over = 0;
            for (JsonNode c : conns) {
                JsonNode comp = c.has("component") ? c.get("component") : c;
                long queuedCount = comp.has("queuedCount") ? comp.get("queuedCount").asLong(0) : 0L;
                long backCount = comp.has("backPressureObjectThreshold") ? comp.get("backPressureObjectThreshold").asLong(Long.MAX_VALUE) : Long.MAX_VALUE;
                if (backCount > 0 && queuedCount >= backCount) over++;
            }
            if (total == 0) return 0.0;
            return 100.0 * over / total;
        } catch (Exception e) {
            log.warn("Error computing backpressure percent: {}", e.getMessage());
            return 0.0;
        }
    }
}

