package com.scoreme.WASMetricsExporter.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoreme.WASMetricsExporter.client.FlowApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
public class GraphBuilder {
    private final FlowApiClient client;
    private final Logger log = LoggerFactory.getLogger(GraphBuilder.class);

    public GraphBuilder(FlowApiClient client) {
        this.client = client;
    }

    /**
     * Recursively build the processor graph across all nested process groups.
     */
    public Map<String, ProcessorNode> buildProcessorMap() throws IOException {
        Map<String, ProcessorNode> map = new HashMap<>();

        JsonNode root = client.get("/process-groups/root");
        if (root == null || !root.has("id")) {
            throw new RuntimeException("Failed to fetch root PG");
        }

        List<String> pgIds = new ArrayList<>();
        crawlProcessGroups(root.get("id").asText(), pgIds);

        for (String pgId : pgIds) {
            try {
                JsonNode procs = client.get("/process-groups/" + pgId + "/processors");
                if (procs != null && procs.has("processors")) {
                    for (JsonNode p : procs.get("processors")) {
                        JsonNode comp = p.get("component");
                        if (comp == null || !comp.has("id")) continue;
                        String id = comp.get("id").asText();
                        String name = comp.has("name") ? comp.get("name").asText() : "-";
                        String type = comp.has("type") ? comp.get("type").asText() : "unknown";
                        ProcessorNode node = new ProcessorNode(id, name, type);
                        if (comp.has("config") && comp.get("config").has("concurrentTasks")) {
                            node.setRequestedConcurrentTasks(comp.get("config").get("concurrentTasks").asInt(1));
                        }
                        map.put(id, node);
                    }
                }

                JsonNode conns = client.get("/process-groups/" + pgId + "/connections");
                if (conns != null && conns.has("connections")) {
                    for (JsonNode c : conns.get("connections")) {
                        JsonNode comp = c.get("component");
                        if (comp == null) continue;
                        JsonNode src = comp.get("source");
                        JsonNode dst = comp.get("destination");
                        if (src == null || dst == null) continue;

                        String srcId = src.has("id") ? src.get("id").asText() : null;
                        String dstId = dst.has("id") ? dst.get("id").asText() : null;
                        if (srcId != null && dstId != null) {
                            map.computeIfAbsent(srcId, id -> new ProcessorNode(id, "unknown-src", "unknown"));
                            map.computeIfAbsent(dstId, id -> new ProcessorNode(id, "unknown-dst", "unknown"));
                            map.get(srcId).getOutgoing().add(dstId);
                            map.get(dstId).getIncoming().add(srcId);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error processing PG {}: {}", pgId, e.getMessage());
            }
        }

        return map;
    }

    /**
     * Recursively adds all PG ids including nested ones.
     */
    private void crawlProcessGroups(String pgId, List<String> out) {
        if (pgId == null) return;
        out.add(pgId);

        try {
            JsonNode subGroups = client.get("/process-groups/" + pgId + "/process-groups");
            if (subGroups != null && subGroups.has("processGroups")) {
                for (JsonNode child : subGroups.get("processGroups")) {
                    if (child.has("id")) {
                        crawlProcessGroups(child.get("id").asText(), out); 
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error crawling sub-process-groups for {}: {}", pgId, e.getMessage());
        }
    }
}
