package com.scoreme.WASMetricsExporter.service;

import ch.qos.logback.core.net.ObjectWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoreme.WASMetricsExporter.client.FlowApiClient;
import com.scoreme.WASMetricsExporter.model.GraphBuilder;
import com.scoreme.WASMetricsExporter.model.ProcessorNode;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class MetricsService {
    private final MeterRegistry registry;
    private final GraphBuilder graphBuilder;
    private final FlowApiClient client;
//    private final Logger log = LoggerFactory.getLogger(MetricsService.class);

    private final AtomicReference<Double> processorCount = new AtomicReference<>(0.0);
    private final AtomicReference<Double> maxPathDepth = new AtomicReference<>(0.0);
    private final AtomicReference<Double> avgFanOut = new AtomicReference<>(0.0);
    private final AtomicReference<Double> ipdCount = new AtomicReference<>(0.0);
    private final AtomicReference<Double> requestedThreads = new AtomicReference<>(0.0);
    private final AtomicReference<Double> scriptedPct = new AtomicReference<>(0.0);
    private final AtomicReference<Double> qbpPct = new AtomicReference<>(0.0);
    private final AtomicReference<Double> heapGrowthMbPerMin = new AtomicReference<>(0.0);

    private final AtomicReference<Double> heapUsedMb = new AtomicReference<>(0.0);
    private final AtomicReference<Double> heapUtilization = new AtomicReference<>(0.0);
    private final AtomicReference<Double> heapMaxMb = new AtomicReference<>(0.0);

    private final AtomicReference<Double> inputPortCount = new AtomicReference<>(0.0);
    private final AtomicReference<Double> outputPortCount = new AtomicReference<>(0.0);

    private final Deque<Long> heapSamples = new ArrayDeque<>();
    private final Deque<Long> heapSampleTimestamps = new ArrayDeque<>();


    public MetricsService(MeterRegistry registry, GraphBuilder graphBuilder, FlowApiClient client) {
        this.registry = registry;
        this.graphBuilder = graphBuilder;
        this.client = client;

        Gauge.builder("flow_processor_count", processorCount, AtomicReference::get).register(registry);
        Gauge.builder("flow_max_path_depth", maxPathDepth, AtomicReference::get).register(registry);
        Gauge.builder("flow_avg_fanout", avgFanOut, AtomicReference::get).register(registry);
        Gauge.builder("flow_ipd_count", ipdCount, AtomicReference::get).register(registry);
        Gauge.builder("flow_requested_threads", requestedThreads, AtomicReference::get).register(registry);
        Gauge.builder("flow_scripted_pct", scriptedPct, AtomicReference::get).register(registry);
        Gauge.builder("flow_qbp_pct", qbpPct, AtomicReference::get).register(registry);
        Gauge.builder("flow_heap_growth_mb_per_min", heapGrowthMbPerMin, AtomicReference::get).register(registry);

        Gauge.builder("flow_heap_used_mb", heapUsedMb, AtomicReference::get).register(registry);
        Gauge.builder("flow_heap_max_mb", heapMaxMb, AtomicReference::get).register(registry);
        Gauge.builder("heapUtilization", heapUtilization, AtomicReference::get).register(registry);
        Gauge.builder("flow_input_port_count", inputPortCount, AtomicReference::get).register(registry);
        Gauge.builder("flow_output_port_count", outputPortCount, AtomicReference::get).register(registry);

    }

    public void refresh() throws IOException {
        Map<String, ProcessorNode> map = graphBuilder.buildProcessorMap();
        if (map == null || map.isEmpty()) {
            log.warn("No processors found in flow");
            return;
        }

        int P = map.size();
        processorCount.set((double) P);

        int D = computeMaxPathDepth(map);
        maxPathDepth.set((double) D);

        double avgF = map.values().stream().mapToInt(n -> n.getOutgoing().size()).average().orElse(0.0);
        avgFanOut.set(avgF);

        long ipd = map.values().stream().filter(n -> (n.getIncoming().size() + n.getOutgoing().size()) > 2).count();
        ipdCount.set((double) ipd);

        int threads = map.values().stream().mapToInt(ProcessorNode::getRequestedConcurrentTasks).sum();
        requestedThreads.set((double) threads);

        long scripted = map.values().stream().filter(n -> isScriptedType(n.getType()) || containsEL(n.getName())).count();
        double scriptedPctVal = 100.0 * scripted / Math.max(P, 1);
        scriptedPct.set(scriptedPctVal);

        double qbpPctVal = computeBackPressurePercent();
        qbpPct.set(qbpPctVal);
//        double inCount = fetchPortCount("/process-groups/root/input-ports", "inputPorts");
        double inCount = graphBuilder.getInputOutputPorts("input");
        inputPortCount.set(inCount);

//        double outCount = fetchPortCount("/process-groups/root/output-ports", "outputPorts");
        double outCount = graphBuilder.getInputOutputPorts("output");
        outputPortCount.set(outCount);


        computeHeapMetrics();

        log.info("\nMetrics updated: \nProcessorCount={}, \ninputPortCount={}, \noutputPortCount={}, \navgFanOut={}, \nipdCount={}, \nscriptedPct=%{}, \nheapUsedMb={} Mb, \nheapMaxMb={} Mb, \nheapGrowthMbPerMin={} Mb",
                P, inputPortCount, outputPortCount,String.format("%.2f",avgF), ipd, String.format("%.2f", scriptedPctVal), String.format("%.2f",heapUsedMb.get()), String.format("%.2f", heapMaxMb.get()), String.format("%.2f",heapGrowthMbPerMin.get()));
    }

    private boolean isScriptedType(String type) {
        if (type == null) return false;
        String t = type.toLowerCase();
        return t.contains("executescript") || t.contains("invokescript") || t.contains("scripted") || t.contains("jython") || t.contains("groovy");
    }

    private boolean containsEL(String text) {
        if (text == null) return false;
        return text.contains("${") || text.contains("#{");
    }

    private int computeMaxPathDepth(Map<String, ProcessorNode> map) {
        Map<String, Integer> memo = new HashMap<>();
        int best = 0;
        for (String id : map.keySet()) {
            best = Math.max(best, dfsDepth(id, map, new HashSet<>(), memo));
        }
        return best;
    }

    private int dfsDepth(String nodeId, Map<String, ProcessorNode> map, Set<String> visiting, Map<String, Integer> memo) {
        if (memo.containsKey(nodeId)) return memo.get(nodeId);
        if (!map.containsKey(nodeId)) return 0;
        if (visiting.contains(nodeId)) return 0; // cycle protection
        visiting.add(nodeId);
        int best = 0;
        for (String nxt : map.get(nodeId).getOutgoing()) {
            best = Math.max(best, 1 + dfsDepth(nxt, map, visiting, memo));
        }
        visiting.remove(nodeId);
        memo.put(nodeId, best);
        return best;
    }

    private double computeBackPressurePercent() {
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

    private void computeHeapMetrics() {
        try {
            JsonNode diag = client.get("/system-diagnostics");
//            log.info("{}", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(diag) );
            if (diag == null || !diag.has("systemDiagnostics")) return;
            JsonNode agg = diag.get("systemDiagnostics").get("aggregateSnapshot");
            if (agg == null) return;

            long heapUsed = agg.has("usedHeapBytes") ? agg.get("usedHeapBytes").asLong(0) : 0L;
            long heapMax = agg.has("maxHeapBytes") ? agg.get("maxHeapBytes").asLong(0) : 0L;
            long heapUtilization = agg.has("heapUtilization") ? agg.get("heapUtilization").asLong(0) : 0L;

            heapUsedMb.set(heapUsed / (1024.0 * 1024.0));
            heapMaxMb.set(heapMax / (1024.0 * 1024.0));

            long now = System.currentTimeMillis();
            heapSamples.addLast(heapUsed);
            heapSampleTimestamps.addLast(now);

            while (heapSampleTimestamps.size() > 20) {
                heapSamples.removeFirst();
                heapSampleTimestamps.removeFirst();
            }

            if (heapSamples.size() >= 2) {
                long earliest = heapSamples.getFirst();
                long latest = heapSamples.getLast();
                long t0 = heapSampleTimestamps.getFirst();
                long t1 = heapSampleTimestamps.getLast();

                double minutes = (t1 - t0) / 60000.0;
                if (minutes >= 0.1 && latest >= earliest) {
                    double growthBytesPerMin = (latest - earliest) / minutes;
                    double growthMbPerMin = growthBytesPerMin / (1024.0 * 1024.0);
                    double prev = heapGrowthMbPerMin.get();
                    double smoothed = prev == 0.0 ? growthMbPerMin : (0.3 * growthMbPerMin + 0.7 * prev);
                    heapGrowthMbPerMin.set(smoothed);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to compute heap metrics: {}", e.getMessage());
        }
    }
//    private double fetchPortCount(String endpoint, String arrayName) {
//        try {
//            JsonNode node = client.get(endpoint);
//            if (node != null && node.has(arrayName)) {
//                return node.get(arrayName).size();
//            }
//        } catch (Exception e) {
//            log.warn("Error fetching {} from {}: {}", arrayName, endpoint, e.getMessage());
//        }
//        return 0.0;
//    }

}
