package com.score_me.was_metrics_exporter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.score_me.was_metrics_exporter.client.FlowApiClient;
import com.score_me.was_metrics_exporter.model.GraphBuilder;
import com.score_me.was_metrics_exporter.entities.ProcessGroupNodeEntity;
import com.score_me.was_metrics_exporter.entities.ProcessorNodeEntity;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Class to produce metrics for prometheus exposure and visualization
 */
@Slf4j

@Component
public class MetricsService {
    private final GraphBuilder graphBuilder;
    private final FlowApiClient client;
    @Getter
    public enum MetricWeight {
        ALPHA(1.0),
        BETA(1.0),
        GAMMA(1.0),
        DELTA(1.0),
        EPSILON(1.0),
        ZETA(1.0),
        ETA(1.0);

        private final double value;
        MetricWeight(double value) {
            this.value = value;
        }
    }

    private final AtomicReference<Double> processorCount = new AtomicReference<>(0.0);
    private final AtomicReference<Double> processorCountFinal = new AtomicReference<>(0.0);
    private final AtomicReference<Double> maxPathDepth = new AtomicReference<>(0.0);
    private final AtomicReference<Double> avgFanOut = new AtomicReference<>(0.0);
    private final AtomicReference<Double> ipdCount = new AtomicReference<>(0.0);
    private final AtomicReference<Double> activeThreads = new AtomicReference<>(0.0);
    private final AtomicReference<Double> scriptedPct = new AtomicReference<>(0.0);
    private final AtomicReference<Double> qbpPct = new AtomicReference<>(0.0);
    private final AtomicReference<Double> heapGrowthMbPerMin = new AtomicReference<>(0.0);

    private final AtomicReference<Double> heapUsedMb = new AtomicReference<>(0.0);
    private final AtomicReference<Double> heapUtilization = new AtomicReference<>(0.0);
    private final AtomicReference<Double> heapMaxMb = new AtomicReference<>(0.0);
    private final AtomicReference<Double> fcsScore = new AtomicReference<>(0.0);

    private final AtomicReference<Double> inputPortCount = new AtomicReference<>(0.0);
    private final AtomicReference<Double> outputPortCount = new AtomicReference<>(0.0);

    private final Deque<Long> heapSamples = new ArrayDeque<>();
    private final Deque<Long> heapSampleTimestamps = new ArrayDeque<>();


    public MetricsService(MeterRegistry registry, GraphBuilder graphBuilder, FlowApiClient client) {
        this.graphBuilder = graphBuilder;
        this.client = client;

        Gauge.builder("flow_processor_count", processorCount, AtomicReference::get).register(registry);
        Gauge.builder("flow_actual_processor_count", processorCountFinal, AtomicReference::get).register(registry);
        Gauge.builder("flow_max_path_depth", maxPathDepth, AtomicReference::get).register(registry);
        Gauge.builder("flow_avg_fanout", avgFanOut, AtomicReference::get).register(registry);
        Gauge.builder("flow_ipd_count", ipdCount, AtomicReference::get).register(registry);
        Gauge.builder("flow_active_threads", activeThreads, AtomicReference::get).register(registry);
        Gauge.builder("flow_scripted_pct", scriptedPct, AtomicReference::get).register(registry);
        Gauge.builder("flow_qbp_pct", qbpPct, AtomicReference::get).register(registry);
        Gauge.builder("flow_heap_growth_mb_per_min", heapGrowthMbPerMin, AtomicReference::get).register(registry);
        Gauge.builder("FCS_SCORE", fcsScore, AtomicReference::get).register(registry);

        Gauge.builder("flow_heap_used_mb", heapUsedMb, AtomicReference::get).register(registry);
        Gauge.builder("flow_heap_max_mb", heapMaxMb, AtomicReference::get).register(registry);
        Gauge.builder("flow_heap_utilization", heapUtilization, AtomicReference::get).register(registry);
        Gauge.builder("flow_input_port_count", inputPortCount, AtomicReference::get).register(registry);
        Gauge.builder("flow_output_port_count", outputPortCount, AtomicReference::get).register(registry);

    }

    public void refresh() throws IOException {
        Map<String, ProcessorNodeEntity> map = graphBuilder.buildProcessorMap();
        Map<String, ProcessGroupNodeEntity> pgMap = graphBuilder.buildProcessGroupMap();
        if (map == null || map.isEmpty()) {
            log.warn("No processors found in flow");
            return;
        }

        int P = map.size();
        processorCount.set((double) P);



        int D = computeMaxPGDepth(pgMap);
        maxPathDepth.set((double) D);

        double avgF = map.values().stream().mapToInt(n -> n.getOutgoing().size()).average().orElse(0.0);
        avgFanOut.set(avgF);

        long ipd = map.values().stream().filter(n -> (n.getIncoming().size() + n.getOutgoing().size()) > 2).count();
        ipdCount.set((double) ipd);

        int threads = map.values().stream().mapToInt(ProcessorNodeEntity::getActiveThreadCount).sum();
        activeThreads.set((double) threads);

        long scripted = map.values().stream().filter(n -> isScriptedType(n.getType()) || containsEL(n.getName())).count();
        double scriptedPctVal = 100.0 * scripted / Math.max(P, 1);
        scriptedPct.set(scriptedPctVal);

        double qbpPctVal = computeBackPressurePercent();
        qbpPct.set(qbpPctVal);

        double inCount = graphBuilder.getInputOutputPorts("input");
        inputPortCount.set(inCount);


        double outCount = graphBuilder.getInputOutputPorts("output");
        outputPortCount.set(outCount);

        double processorCountCalculated = map.size() - inCount - outCount;
        processorCountFinal.set(processorCountCalculated);


        computeHeapMetrics();

        double score = (MetricWeight.ALPHA.getValue() * processorCountFinal.get())
                + (MetricWeight.BETA.getValue() * maxPathDepth.get())
                + (MetricWeight.GAMMA.getValue() * avgFanOut.get())
                + (MetricWeight.DELTA.getValue() * activeThreads.get())
                + (MetricWeight.EPSILON.getValue() * scriptedPct.get())
                + (MetricWeight.ZETA.getValue() * qbpPct.get())
                + (MetricWeight.ETA.getValue() * heapGrowthMbPerMin.get());

        fcsScore.set(score);


        log.info("\nMetrics updated: \nFlow Complexity Score= {}, \nProcessorCount = {},\nProcessor Count (after removing I/O ports) = {},  \ninputPortCount = {}, \noutputPortCount={}, \navgFanOut={}, \nmaxProcessGroupDepth = {}, \nconcurrentThreads={}, \nipdCount={}, \nscriptedPct={}%, \nqueueBackPressure={}, \nheapUsedMb={} Mb, \nheapMaxMb={} Mb, \nheapGrowthMbPerMin={} Mb",
                String.format("%.2f",fcsScore.get()),P, processorCountFinal.get(),inputPortCount.get(), outputPortCount.get(),String.format("%.2f",avgF), maxPathDepth.get(), threads, ipd, String.format("%.2f", scriptedPctVal), String.format("%.2f",qbpPct.get()),String.format("%.2f",heapUsedMb.get()), String.format("%.2f", heapMaxMb.get()), String.format("%.2f",heapGrowthMbPerMin.get()));
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


    public int computeMaxPGDepth(Map<String, ProcessGroupNodeEntity> pgMap) {
        Map<String, Integer> memo = new HashMap<>();
        int best = 0;
        for (String id : pgMap.keySet()) {
            best = Math.max(best, dfsPGDepth(id, pgMap, new HashSet<>(), memo));
        }
        return best;
    }

    private int dfsPGDepth(String pgId, Map<String, ProcessGroupNodeEntity> map,
                           Set<String> visiting, Map<String, Integer> memo) {
        if (memo.containsKey(pgId)) return memo.get(pgId);
        if (!map.containsKey(pgId)) return 0;
        if (visiting.contains(pgId)) return 0; // ignore cycles

        visiting.add(pgId);
        int best = 0;
        for (String child : map.get(pgId).getChildren()) {
            best = Math.max(best, 1 + dfsPGDepth(child, map, visiting, memo));
        }
        visiting.remove(pgId);

        memo.put(pgId, best);
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

//            if (heapSamples.size() >= 2) {
//                long earliest = heapSamples.getFirst();
//                long latest = heapSamples.getLast();
//                long t0 = heapSampleTimestamps.getFirst();
//                long t1 = heapSampleTimestamps.getLast();
//
//                double minutes = (t1 - t0) / 60000.0;
//                if (minutes >= 0.1 && latest >= earliest) {
//                    double growthBytesPerMin = (latest - earliest) / minutes;
//                    double growthMbPerMin = growthBytesPerMin / (1024.0 * 1024.0);
//                    double prev = heapGrowthMbPerMin.get();
//                    double smoothed = prev == 0.0 ? growthMbPerMin : (0.3 * growthMbPerMin + 0.7 * prev);
//                    heapGrowthMbPerMin.set(smoothed);
//                }
//            }

            if (heapSamples.size() >= 2) {
                // make local snapshots to avoid concurrent-mod issues
                List<Long> samples = new ArrayList<>(heapSamples);
                List<Long> times = new ArrayList<>(heapSampleTimestamps);

                int n = samples.size();
                long t0 = times.get(0);

                // x in minutes from t0, y in bytes
                double sumX = 0.0, sumY = 0.0, sumXY = 0.0, sumXX = 0.0;
                for (int i = 0; i < n; i++) {
                    double x = (times.get(i) - t0) / 60000.0;
                    double y = samples.get(i).doubleValue();
                    sumX += x;
                    sumY += y;
                    sumXY += x * y;
                    sumXX += x * x;
                }

                double denom = n * sumXX - sumX * sumX;
                double growthBytesPerMin;
                if (Math.abs(denom) > 1e-12) {
                    growthBytesPerMin = (n * sumXY - sumX * sumY) / denom;
                } else {
                    // fallback to simple delta if regression is degenerate
                    double minutes = (times.get(n - 1) - times.getFirst()) / 60000.0;
                    if (minutes < 1e-9) return;
                    growthBytesPerMin = (samples.get(n - 1) - samples.getFirst()) / minutes;
                }

                double growthMbPerMin = growthBytesPerMin / (1024.0 * 1024.0);

                double prev = heapGrowthMbPerMin.get();
                double smoothed = Double.isNaN(prev) ? growthMbPerMin : (0.3 * growthMbPerMin + 0.7 * prev);
                heapGrowthMbPerMin.set(smoothed);
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
