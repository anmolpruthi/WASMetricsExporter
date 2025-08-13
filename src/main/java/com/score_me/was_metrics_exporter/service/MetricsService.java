package com.score_me.was_metrics_exporter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.score_me.was_metrics_exporter.client.FlowApiClient;
import com.score_me.was_metrics_exporter.helper.MethodHelper;
import com.score_me.was_metrics_exporter.model.GraphBuilder;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service that computes various metrics for the NiFi flow.
 * It uses a {@link GraphBuilder} to build the processor graph and fetches metrics from the Flow API.
 */
@Slf4j

@Service
public class MetricsService {
    private final GraphBuilder graphBuilder;
    private final FlowApiClient client;

    private final MethodHelper methodHelper;

    private final MeterRegistry meterRegistry;
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

    private final AtomicReference<Double> processorCountStarter = new AtomicReference<>(0.0);
    private final AtomicReference<Double> processorCountFinal = new AtomicReference<>(0.0);
    private final AtomicReference<Double> maxPathDepth = new AtomicReference<>(0.0);
    private final AtomicReference<Double> avgFanOut = new AtomicReference<>(0.0);
    private final AtomicReference<Double> ipdCount = new AtomicReference<>(0.0);
    private final AtomicReference<Double> activeThreads = new AtomicReference<>(0.0);
    private final AtomicReference<Double> scriptedPct = new AtomicReference<>(0.0);
    private final AtomicReference<Double> qbpPct = new AtomicReference<>(0.0);
    private final AtomicReference<Double> heapGrowthMbPerMin = new AtomicReference<>(0.0);

    private final AtomicReference<Double> heapUsedMb = new AtomicReference<>(0.0);
    private final AtomicReference<Double> heapUtilizationMb = new AtomicReference<>(0.0);
    private final AtomicReference<Double> heapMaxMb = new AtomicReference<>(0.0);
    private final AtomicReference<Double> fcsScore = new AtomicReference<>(0.0);
    private final AtomicReference<Double> avgCpuUsage = new AtomicReference<>(0.0);
    private final AtomicReference<Double> inputPortCount = new AtomicReference<>(0.0);
    private final AtomicReference<Double> outputPortCount = new AtomicReference<>(0.0);

    private final Deque<Double> cpuSamples = new ConcurrentLinkedDeque<>();
    private final int maxCpuSamples = 60;
    private final Deque<Long> heapSamples = new ArrayDeque<>();
    private final Deque<Long> heapSampleTimestamps = new ArrayDeque<>();


    @Autowired
    public MetricsService(MeterRegistry registry, GraphBuilder graphBuilder, FlowApiClient client, MethodHelper methodHelper) {
        this.graphBuilder = graphBuilder;
        this.client = client;
        this.methodHelper = methodHelper;
        this.meterRegistry = registry;

        Gauge.builder("flow_processor_count", processorCountStarter, AtomicReference::get).register(registry);
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
        Gauge.builder("flow_heap_utilization", heapUtilizationMb, AtomicReference::get).register(registry);
        Gauge.builder("flow_input_port_count", inputPortCount, AtomicReference::get).register(registry);
        Gauge.builder("flow_output_port_count", outputPortCount, AtomicReference::get).register(registry);

        Gauge.builder("flow_cpu_avg_usage", avgCpuUsage, AtomicReference::get)
                .description("Average system CPU usage over the last " + maxCpuSamples + " refresh cycles")
                .register(registry);

    }

    public void refresh() throws IOException {

//        Map<String, ProcessorNodeEntity> processorMap = graphBuilder.buildProcessorMap("root");
//        Map<String, ProcessGroupNodeEntity> pgMap = graphBuilder.buildProcessGroupMap("root");


//        if (processorMap == null || processorMap.isEmpty()) {
//            log.warn("No processors found in flow");
//            return;
//        }


        double processorCount = 0;
        double maxPGDepth = 0;
        double avgF = 0;
        double ipd = 0;
        double activeThreadsCount = 0;
        double scriptedPctVal = 0;
        double qbpPctVal = 0;
        double inputCount = 0;
        double outputCount = 0;
        double processorCountCalculated = 0;

        try {
            Map<String, Double> metrics = methodHelper.getMetrics(client, "root");
            log.info(metrics.toString());
//            processorCount = methodHelper.getMetrics(client, "root").get("processorCount");
            processorCount = metrics.get("processorCount");
            processorCountStarter.set(processorCount);

//            maxPGDepth = methodHelper.getMetrics(client, "root").get("maxPathDepth");
            maxPGDepth = metrics.get("maxPathDepth");
            maxPathDepth.set(maxPGDepth);

//            avgF = methodHelper.getMetrics(client, "root").get("avgF");
            avgF = metrics.get("avgF");
            avgFanOut.set(avgF);

//            activeThreadsCount = methodHelper.getMetrics(client, "root").get("activeThreads");
            activeThreadsCount = metrics.get("activeThreads");
            activeThreads.set(activeThreadsCount);

//            ipd = methodHelper.getMetrics(client, "root").get("ipd");
            ipd = metrics.get("ipd");
            ipdCount.set(ipd);

//            scriptedPctVal = methodHelper.getMetrics(client, "root").get("scriptedPctVal");
            scriptedPctVal = metrics.get("scriptedPctVal");
            scriptedPct.set(scriptedPctVal);

//            qbpPctVal = methodHelper.getMetrics(client, "root").get("qbpPctVal");
            qbpPctVal = metrics.get("qbpPctVal");
            qbpPct.set(qbpPctVal);

            inputCount = metrics.get("inputPortCount");
            inputPortCount.set(inputCount);

            outputCount = metrics.get("outputPortCount");
            outputPortCount.set(outputCount);

            processorCountCalculated = processorCount - inputCount - outputCount;
            processorCountFinal.set(processorCountCalculated);



        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e.getCause());
        }

        computeHeapMetrics();

        calculateAvgCpuUsage(meterRegistry);


//        int maxPGDepth = computeMaxPGDepth(pgMap);
//        maxPathDepth.set((double) maxPGDepth);
//
//        double avgF = processorMap.values().stream().mapToInt(n -> n.getOutgoing().size()).average().orElse(0.0);
//        avgFanOut.set(avgF);
//
//        long ipd = processorMap.values().stream().filter(n -> (n.getIncoming().size() + n.getOutgoing().size()) > 2).count();
//        ipdCount.set((double) ipd);
//
//        int threads = processorMap.values().stream().mapToInt(ProcessorNodeEntity::getActiveThreadCount).sum();
//        activeThreads.set((double) threads);
//
//        long scripted = processorMap.values().stream().filter(n -> isScriptedType(n.getType()) || containsEL(n.getName())).count();
//        double scriptedPctVal = 100.0 * scripted / Math.max(processorCount, 1);
//        scriptedPct.set(scriptedPctVal);
//
//        double qbpPctVal = computeBackPressurePercent();
//        qbpPct.set(qbpPctVal);
//
//        double inCount = methodHelper.getInputOutputPortMap("input", "root");
//        inputPortCount.set(inCount);
//
//
//        double outCount = methodHelper.getInputOutputPortMap("output", "root");
//        outputPortCount.set(outCount);
//
//        double processorCountCalculated = processorMap.size() - inCount - outCount;
//        processorCountFinal.set(processorCountCalculated);




        double score = (MetricWeight.ALPHA.getValue() * processorCountFinal.get())
                + (MetricWeight.BETA.getValue() * maxPathDepth.get())
                + (MetricWeight.GAMMA.getValue() * avgFanOut.get())
                + (MetricWeight.DELTA.getValue() * activeThreads.get())
                + (MetricWeight.EPSILON.getValue() * scriptedPct.get())
                + (MetricWeight.ZETA.getValue() * qbpPct.get())
                + (MetricWeight.ETA.getValue() * heapGrowthMbPerMin.get());

        fcsScore.set(score);


//        log.info("\nMetrics updated: \nFlow Complexity Score= {}, \nProcessorCount = {},\nProcessor Count (after removing I/O ports) = {},  \ninputPortCount = {}, \noutputPortCount={}, \navgFanOut={}, \nmaxProcessGroupDepth = {}, \nconcurrentThreads={}, \nipdCount={}, \nscriptedPct={}%, \nqueueBackPressure={}, \nheapUsedMb={} Mb, \nheapMaxMb={} Mb, \nheapGrowthMbPerMin={} Mb",
//                String.format("%.2f",fcsScore.get()),processorCountStarter.get(), processorCountFinal.get(),inputPortCount.get(), outputPortCount.get(),String.format("%.2f",avgF), maxPathDepth.get(), activeThreads.get(), ipd, String.format("%.2f", scriptedPct.get()), String.format("%.2f",qbpPct.get()),String.format("%.2f",heapUsedMb.get()), String.format("%.2f", heapMaxMb.get()), String.format("%.2f",heapGrowthMbPerMin.get()));
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
            heapUtilizationMb.set(heapUtilization / (1024.0 * 1024.0));
            long now = System.currentTimeMillis();
            heapSamples.addLast(heapUsed);
            heapSampleTimestamps.addLast(now);

            while (heapSampleTimestamps.size() > 20) {
                heapSamples.removeFirst();
                heapSampleTimestamps.removeFirst();
            }
            calculateHeapGrowthPerMin();

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


        } catch (Exception e) {
            log.warn("Failed to compute heap metrics: {}", e.getMessage());
        }
    }

    private void calculateHeapGrowthPerMin(){
        try{
            if (heapSamples.size() >= 2) {
                // make local snapshots to avoid concurrent-mod issues
                List<Long> samples = new ArrayList<>(heapSamples);
                List<Long> times = new ArrayList<>(heapSampleTimestamps);

                int n = samples.size();
                long t0 = times.get(0);

                // x in minutes from t0, y in bytes
                double sumX = 0.0;
                double sumY = 0.0;
                double sumXY = 0.0;
                double sumXX = 0.0;
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
        }
        catch (Exception e){
            log.warn("Failed to calculate heap growth per min: {}", e.getMessage());
        }
    }

    private void calculateAvgCpuUsage(MeterRegistry meterRegistry) {
        try {
            Double cpuValue = meterRegistry.find("system.cpu.usage").gauge() != null
                    ? meterRegistry.find("system.cpu.usage").gauge().value()
                    : null;

            if (cpuValue != null && !cpuValue.isNaN()) {
                cpuSamples.add(cpuValue);
                if (cpuSamples.size() > maxCpuSamples) {
                    cpuSamples.pollFirst();
                }
                avgCpuUsage.set(cpuSamples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
            }
        } catch (Exception e) {
            log.warn("Failed to read CPU usage: {}", e.getMessage());
        }
    }

}
