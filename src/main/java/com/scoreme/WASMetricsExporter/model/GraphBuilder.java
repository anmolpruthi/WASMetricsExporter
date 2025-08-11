package com.scoreme.WASMetricsExporter.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoreme.WASMetricsExporter.client.FlowApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.rmi.StubNotFoundException;
import java.util.*;

@Slf4j
@Component
public class GraphBuilder {
    private static final String PG_ENDPOINT = "/process-groups/";
    private final FlowApiClient client;

    public GraphBuilder(FlowApiClient client) {
        this.client = client;
    }

    /**
     * Recursively build the processor graph across all nested process groups.
     */
    public Map<String, ProcessorNode> buildProcessorMap() throws IOException {
        Map<String, ProcessorNode> map = new HashMap<>();

        JsonNode root = client.get(PG_ENDPOINT + "root");
        JsonNode processors = client.get(PG_ENDPOINT + "root/processors");
//        log.info("{}", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(processors));
//        log.info("{}", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root));

        if (root == null || !root.has("id")) {
            throw new IOException("Failed to fetch root PG");
        }

        List<String> pgIds = new ArrayList<>();
        crawlProcessGroups(root.get("id").asText(), pgIds);

        for (String pgId : pgIds) {
            try {
                JsonNode procs = client.get(PG_ENDPOINT + pgId + "/processors");
                if (procs != null && procs.has("processors")) {
                    for (JsonNode p : procs.get("processors")) {
                        JsonNode comp = p.get("component");
                        if (comp == null || !comp.has("id")) continue;
                        String id = comp.get("id").asText();
                        String name = comp.has("name") ? comp.get("name").asText() : "-";
                        String type = comp.has("type") ? comp.get("type").asText() : "unknown";
                        ProcessorNode node = new ProcessorNode(id, name, type);
//                        log.info("{}", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(node));
                        if (comp.has("config") && comp.get("config").has("concurrentTasks")) {
                            node.setRequestedConcurrentTasks(comp.get("config").get("concurrentTasks").asInt(1));
                        }
                        map.put(id, node);
                    }
                }

                JsonNode conns = client.get(PG_ENDPOINT + pgId + "/connections");
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

    public Map<String, ProcessGroupNode> buildProcessGroupMap() throws IOException {
        Map<String, ProcessGroupNode> pgMap = new HashMap<>();

        JsonNode root = client.get(PG_ENDPOINT + "root");
        if (root == null || !root.has("id")) {
            throw new IOException("Failed to fetch root PG");
        }
        String rootId = root.get("id").asText();
        crawlProcessGroupHierarchy(rootId, pgMap);
        return pgMap;
    }

    public int getInputOutputPorts(String portType) throws IOException {
        Map<String, String> portMap = new HashMap<>();
        String portEndpoint;
        String fieldName;
        if (portType.equals("input")) { portEndpoint = "/input-ports";} else if (portType.equals("output")) { portEndpoint = "/output-ports";} else {throw new IOException("Invalid port type");}
        if (portType.equals("input")) { fieldName = "inputPorts";} else if (portType.equals("output")) { fieldName = "outputPorts";} else {throw new IOException("Invalid port type");}

        JsonNode root = client.get(PG_ENDPOINT + "root");
        if (root == null || !root.has("id")) {
            throw new IOException("Failed to fetch root PG");
        }
        List<String> pgIds = new ArrayList<>();
        crawlProcessGroups(root.get("id").asText(), pgIds);
//        log.info("{}", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(pgIds));
        for(String pgId : pgIds) {
            try {
                JsonNode ports = client.get(PG_ENDPOINT + pgId + portEndpoint);
                if (ports != null && ports.has(fieldName)) {
                    for (JsonNode portName : ports.get(fieldName)) {
                        String id = portName.get("id").asText();
                        String name = portName.get("component").get("name").asText();
                        portMap.put(id, name);
//                        log.info("{}", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(portName));
                    }
                }
            }catch (Exception e) {
                log.error("Error processing PG {}: {}", pgId, e.getMessage());
            }
//            JsonNode ports = client.get(PG_ENDPOINT + pgId + "/input-ports");
//            log.info(ports.toString());
//            if (ports != null && ports.has(fieldName)) {
//                    for (JsonNode port : ports.get(fieldName)) {
//                        String id = port.get("id").asText();
//                        String name = port.get("component").get("name").asText();
//                        portMap.put(id, name);
//                        log.info("{}", new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(port));
//                    }
//                }
        }
        return portMap.size();
    }

    /**
     * Recursively adds all PG ids including nested ones.
     */
    private void crawlProcessGroups(String pgId, List<String> out) {
        if (pgId == null) return;
        out.add(pgId);

        try {
            JsonNode subGroups = client.get(PG_ENDPOINT + pgId + "/process-groups");
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

    private void crawlProcessGroupHierarchy(String pgId, Map<String, ProcessGroupNode> pgMap) throws IOException {
        JsonNode pg = client.get(PG_ENDPOINT + pgId);
        if (pg == null || !pg.has("component")) return;

        String name = pg.get("component").has("name") ? pg.get("component").get("name").asText() : "-";
        ProcessGroupNode node = new ProcessGroupNode(pgId, name);
        pgMap.put(pgId, node);

        // Fetch child PGs
        JsonNode childGroups = client.get(PG_ENDPOINT + pgId + "/process-groups");
        if (childGroups != null && childGroups.has("processGroups")) {
            for (JsonNode child : childGroups.get("processGroups")) {
                JsonNode comp = child.get("component");
                if (comp != null && comp.has("id")) {
                    String childId = comp.get("id").asText();
                    node.getChildren().add(childId);
                    crawlProcessGroupHierarchy(childId, pgMap); // recurse
                }
            }
        }
    }
}
