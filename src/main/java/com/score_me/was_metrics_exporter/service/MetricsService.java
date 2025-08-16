package com.score_me.was_metrics_exporter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.score_me.was_metrics_exporter.client.FlowApiClient;
import com.score_me.was_metrics_exporter.helper.MethodHelper;
import com.score_me.was_metrics_exporter.utils.ExportToFile;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service that computes various metrics for the NiFi flow.
 * It uses {@link MethodHelper} to fetch metrics from the Flow API.
 */
@Slf4j
@Service
public class MetricsService {

    private final DecimalFormat df = new DecimalFormat("#.##");
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

    private final AtomicReference<Double> fcsScore = new AtomicReference<>(0.0);
    private final AtomicReference<Double> inputPortCount = new AtomicReference<>(0.0);
    private final AtomicReference<Double> outputPortCount = new AtomicReference<>(0.0);


    /**
     * Heap Metrics Tracking
     */
    private final Deque<Long> heapSamples = new ArrayDeque<>();
    private final Deque<Long> heapSampleTimestamps = new ArrayDeque<>();
    private final AtomicReference<Double> heapGrowthMbPerMin = new AtomicReference<>(0.0);

    private final AtomicReference<Double> heapUsedMb = new AtomicReference<>(0.0);
    private final AtomicReference<Double> heapUtilizationMb = new AtomicReference<>(0.0);
    private final AtomicReference<Double> heapMaxMb = new AtomicReference<>(0.0);

    /**
     * CPU Usage Tracking
     */
//    private double cpuSum = 0.0;
//    private long totalCpuSampleCount = 0;

    // 60 samples per minute, 60 minutes per hour, 24 hours per day
    private final int maxCpuSamples = 60*60*24;
    private static double spikeThreshold;
    private Long spikeStartTime = null;



    private final Deque<Double> cpuSamples = new ConcurrentLinkedDeque<>();
    /**
     * The maximum number of CPU samples to keep for calculating the average CPU usage.
     * This is set to 60, which means the average will be calculated over the last 60 samples.
     */


    private final AtomicReference<Double> lifeTimeAvgCpuUsage = new AtomicReference<>(0.0);

    private final AtomicReference<Double> windowAvgCpuUsage = new AtomicReference<>(0.0);

    private final AtomicReference<Double> instantaneousCpuUsage = new AtomicReference<>(0.0);

    private final AtomicReference<Double> spikeRecoveryTime = new AtomicReference<>(null);



    /**
     * Constructor for MetricsService.
     * Initializes the service with the provided MeterRegistry, FlowApiClient, and MethodHelper.
     * @param registry
     * @param client
     * @param methodHelper
     */


    @Autowired
    public MetricsService(MeterRegistry registry, FlowApiClient client, MethodHelper methodHelper) {
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

        Gauge.builder("window_avg_cpu_usage", windowAvgCpuUsage, AtomicReference::get)
                .description("Average system CPU usage over the last " + maxCpuSamples + " refresh cycles")
                .register(registry);
//        Gauge.builder("lifetime_avg_cpu_usage", lifeTimeAvgCpuUsage, AtomicReference::get)
//                .description("Lifetime average system CPU usage")
//                .register(registry);
        Gauge.builder("instantaneous_cpu_usage", instantaneousCpuUsage, AtomicReference::get)
                .description("Instantaneous system CPU usage")
                .register(registry);
        Gauge.builder("spike_recovery_time", () ->
                Optional.ofNullable(spikeRecoveryTime.get()).orElse(0.0)
        ).register(registry);

//        spikeThreshold = 1.0; // Initialize with a default value to avoid NaN
//        spikeRecoveryTime.set(null); // Initialize recovery time to null
        log.info("MetricsService initialized with spike threshold: {}", spikeThreshold);

    }

    public void refresh() throws IOException {
        log.info("Refreshing metrics...");
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



            computeHeapMetrics();
//            log.info("Heap metrics computed: heapUsedMb={}, heapMaxMb={}, heapUtilizationMb={}, heapGrowthMbPerMin={}",
//                    df.format(heapUsedMb.get()), df.format(heapMaxMb.get()), df.format(heapUtilizationMb.get()), df.format(heapGrowthMbPerMin.get()));

            metrics.put("heapUsedMb", heapUsedMb.get());
            metrics.put("heapMaxMb", heapMaxMb.get());
//            metrics.put("heapUtilizationMb", heapUtilizationMb.get());
            metrics.put("heapGrowthMbPerMin", heapGrowthMbPerMin.get());

            calculateAvgCpuUsage(meterRegistry);
            metrics.put("windowAvgCpuUsage", windowAvgCpuUsage.get());
            metrics.put("instantaneousCpuUsage", instantaneousCpuUsage.get());
            double score = (MetricWeight.ALPHA.getValue() * processorCountFinal.get())
                    + (MetricWeight.BETA.getValue() * maxPathDepth.get())
                    + (MetricWeight.GAMMA.getValue() * avgFanOut.get())
                    + (MetricWeight.DELTA.getValue() * activeThreads.get())
                    + (MetricWeight.EPSILON.getValue() * scriptedPct.get())
                    + (MetricWeight.ZETA.getValue() * qbpPct.get())
                    + (MetricWeight.ETA.getValue() * heapGrowthMbPerMin.get());

            fcsScore.set(score);
            metrics.put("fcsScore", fcsScore.get());
            metrics.put("SpikeRecoveryTimeMs", (spikeRecoveryTime.get() != null ? spikeRecoveryTime.get() : 0.0));
            metrics.put("SpikeRecoveryTimeSec", (spikeRecoveryTime.get() != null ? spikeRecoveryTime.get() / 1000.0 : 0.0));
//            log.info("CPU metrics computed: currentWindowAvgCpuUsage={}, \n Instantaneous CPU Usage = {} \nCurrent FCS Score = {}", df.format(windowAvgCpuUsage.get()), df.format(instantaneousCpuUsage.get()), fcsScore.get());
            log.info("Metrics:\n{}",
                    metrics.entrySet().stream()
                            .map(e -> e.getKey() + " = " + df.format(e.getValue()))
                            .reduce("", (a, b) -> a + "\n" + b)
            );
            ExportToFile.exportToExcel(metrics, "metrics.xlsx");
            log.info("Metrics exported to Excel file: {}", new File("metrics.xlsx").getAbsolutePath());
            ExportToFile.exportToTxt(metrics, "metrics.txt");
            log.info("Metrics exported to Txtfile: {}", new File("metrics.txt").getAbsolutePath());

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e.getCause());
        }

//        log.info("\nMetrics updated: \nFlow Complexity Score= {}, \nProcessorCount = {},\nProcessor Count (after removing I/O ports) = {},  \ninputPortCount = {}, \noutputPortCount={}, \navgFanOut={}, \nmaxProcessGroupDepth = {}, \nconcurrentThreads={}, \nipdCount={}, \nscriptedPct={}%, \nqueueBackPressure={}, \nheapUsedMb={} Mb, \nheapMaxMb={} Mb, \nheapGrowthMbPerMin={} Mb",
//                String.format("%.2f",fcsScore.get()),processorCountStarter.get(), processorCountFinal.get(),inputPortCount.get(), outputPortCount.get(),String.format("%.2f",avgF), maxPathDepth.get(), activeThreads.get(), ipd, String.format("%.2f", scriptedPct.get()), String.format("%.2f",qbpPct.get()),String.format("%.2f",heapUsedMb.get()), String.format("%.2f", heapMaxMb.get()), String.format("%.2f",heapGrowthMbPerMin.get()));
    }


    private void computeHeapMetrics() {
        try {
            JsonNode diag = client.get("/system-diagnostics");
            if (diag == null || !diag.has("systemDiagnostics")) return;
            JsonNode agg = diag.get("systemDiagnostics").get("aggregateSnapshot");
            if (agg == null) return;

            long heapUsed = agg.has("usedHeapBytes") ? agg.get("usedHeapBytes").asLong(0) : 0L;
            long heapMax = agg.has("maxHeapBytes") ? agg.get("maxHeapBytes").asLong(0) : 0L;
            double heapUtilizationValue = heapMax > 0 ? (heapUsed / (double) heapMax) * 100.0 : 0.0;
            heapUsedMb.set(heapUsed / (1024.0 * 1024.0));
            heapMaxMb.set(heapMax / (1024.0 * 1024.0));
            heapUtilizationMb.set(heapUtilizationValue / (1024.0 * 1024.0));
            long now = System.currentTimeMillis();
            heapSamples.addLast(heapUsed);
            heapSampleTimestamps.addLast(now);

//            while (heapSampleTimestamps.size() > 60*60*24) { // keep samples for 24 hours
//                // remove the oldest sample if we exceed the limit
//                heapSamples.removeFirst();
//                heapSampleTimestamps.removeFirst();
//            }
            if (heapSamples.size() > 60 * 60 * 24) { // keep samples for 24 hours
                // remove the oldest sample if we exceed the limit
                heapSamples.removeFirst();
                heapSampleTimestamps.removeFirst();
            }
            calculateHeapGrowthPerMin();

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
                long t0 = times.getFirst();

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
                    ? Objects.requireNonNull(meterRegistry.find("system.cpu.usage").gauge()).value()
                    : null;

            log.info(String.valueOf(cpuValue));
            log.info("{}", System.currentTimeMillis());

            if (cpuValue != null && !cpuValue.isNaN()) {

                double percentage = cpuValue * 100;

                percentage = Math.round(percentage * 100.0) / 100.0;
                log.info("CPU Usage percentage : {}", percentage);
                instantaneousCpuUsage.set(cpuValue*100);

                cpuSamples.add(percentage);
                if (cpuSamples.size() > maxCpuSamples) {
                    cpuSamples.pollFirst();
                }

                double avg = cpuSamples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                windowAvgCpuUsage.set(Math.round(avg * 100.0) / 100.0);
                spikeThreshold = avg + (avg * 0.2);
                log.info("Spike Threshold set to: {}", spikeThreshold);
                if (spikeThreshold < 1) {
                    spikeThreshold = 1;
                }
                if (cpuValue*100 > spikeThreshold) {
                    if (spikeStartTime == null) {
                        spikeStartTime = System.currentTimeMillis();
                        log.warn("CPU spike started: {}", spikeStartTime);
                        spikeRecoveryTime.set(0.0); // Reset recovery time when a spike starts
                    }
                } else if (spikeStartTime != null) {
                    double recoveryTime = System.currentTimeMillis() - (double)spikeStartTime;
                    spikeRecoveryTime.set(recoveryTime);
                    log.warn("CPU spike ended. Recovery time: {} ms", recoveryTime);
                    spikeStartTime = null;
                }

            }
        } catch (Exception e) {
            log.warn("Failed to read CPU usage: {}", e.getMessage());
        }
    }
}
