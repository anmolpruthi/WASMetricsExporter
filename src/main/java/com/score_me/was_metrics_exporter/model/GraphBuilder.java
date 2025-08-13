package com.score_me.was_metrics_exporter.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.score_me.was_metrics_exporter.client.FlowApiClient;
import com.score_me.was_metrics_exporter.entities.ProcessGroupNodeEntity;
import com.score_me.was_metrics_exporter.entities.ProcessorNodeEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Slf4j
@Component
public abstract class GraphBuilder {
    private static final String PG_ENDPOINT = "/process-groups/";
    private final FlowApiClient client;

    public GraphBuilder(FlowApiClient client) {
        this.client = client;
    }
    private JsonNode getRootPg() throws IOException{
        JsonNode root = client.get(PG_ENDPOINT + "root");
        if (root == null || !root.has("id")) {
            throw new IOException("Failed to fetch root PG");
        }
        return root;
    }




    /**
     * Recursively build the processor graph across all nested process groups.
     */
    public Map<String, ProcessorNodeEntity> buildProcessorMap() throws IOException {
        Map<String, ProcessorNodeEntity> map = new HashMap<>();

        JsonNode root = getRootPg();


        List<String> pgIds = new ArrayList<>();
        crawlProcessGroups(root.get("id").asText(), pgIds);

        for (String pgId : pgIds) {
            try {
                JsonNode procs = client.get(PG_ENDPOINT + pgId + "/processors");
                if (procs != null && procs.has("processors")) {
                    for (JsonNode p : procs.get("processors")) {
                        JsonNode status =  p.get("status");
                        if (status == null || !status.has("id")) continue;
                        String id = status.get("aggregateSnapshot").get("id").asText();
                        String name = status.has("aggregateSnapshot") ? status.get("aggregateSnapshot").get("name").asText() : "-";
                        String type = status.has("aggregateSnapshot") ? status.get("aggregateSnapshot").get("type").asText() : "unknown";
                        int activeThreadCount = status.has("aggregateSnapshot") ? status.get("aggregateSnapshot").get("activeThreadCount").asInt() : 0;



                        ProcessorNodeEntity node = new ProcessorNodeEntity(id, name, type);
                        node.setActiveThreadCount(activeThreadCount);
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
                            map.computeIfAbsent(srcId, id -> new ProcessorNodeEntity(id, "unknown-src", "unknown"));
                            map.computeIfAbsent(dstId, id -> new ProcessorNodeEntity(id, "unknown-dst", "unknown"));
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
     * Method to build a graph of all the process-groups in that particular node
     * @return Graph of all process-groups
     * @throws IOException
     */
    public Map<String, ProcessGroupNodeEntity> buildProcessGroupMap() throws IOException {
        Map<String, ProcessGroupNodeEntity> pgMap = new HashMap<>();

        JsonNode root = getRootPg();
        String rootId = root.get("id").asText();
        crawlProcessGroupHierarchy(rootId, pgMap);
        return pgMap;
    }

    /**
     * @param portType
     * @return The number of ports (Input/output) on that node
     * @throws IOException
     */
    public int getInputOutputPorts(String portType) throws IOException {
        Map<String, String> portMap = new HashMap<>();
        String portEndpoint;
        String fieldName;
        if (portType.equals("input")) { portEndpoint = "/input-ports";} else if (portType.equals("output")) { portEndpoint = "/output-ports";} else {throw new IOException("Invalid port type");}
        if (portType.equals("input")) { fieldName = "inputPorts";} else if (portType.equals("output")) { fieldName = "outputPorts";} else {throw new IOException("Invalid port type");}

        JsonNode root = getRootPg();
        if (root == null || !root.has("id")) {
            throw new IOException("Failed to fetch root PG");
        }


        List<String> pgIds = new ArrayList<>();
        crawlProcessGroups(root.get("id").asText(), pgIds);
        for(String pgId : pgIds) {
            try {
                JsonNode ports = client.get(PG_ENDPOINT + pgId + portEndpoint);
                if (ports != null && ports.has(fieldName)) {
                    for (JsonNode portName : ports.get(fieldName)) {
                        String id = portName.get("id").asText();
                        String name = portName.get("component").get("name").asText();
                        portMap.put(id, name);
                    }
                }
            }catch (Exception e) {
                log.error("Error processing PG {}: {}", pgId, e.getMessage());
            }
        }
        return portMap.size();
    }

    /**
     * Builder method for controller
     * @param pgId
     * @return Graph of all the processors in the specified process group
     * @throws IOException
     */
    public Map<String, ProcessorNodeEntity> buildProcessorMapForPg(String pgId) throws IOException {
        Map<String, ProcessorNodeEntity> map = new HashMap<>();
        List<String> pgIds = new ArrayList<>();
        crawlProcessGroups(pgId, pgIds); // includes nested PGs

        for (String id : pgIds) {
            try {
                JsonNode procs = client.get(PG_ENDPOINT + id + "/processors");
                if (procs != null && procs.has("processors")) {
                    for (JsonNode p : procs.get("processors")) {
                        JsonNode status = p.get("status");
                        if (status == null || !status.has("aggregateSnapshot")) continue;

                        JsonNode snap = status.get("aggregateSnapshot");
                        String procId = snap.has("id") ? snap.get("id").asText() : null;
                        String name = snap.has("name") ? snap.get("name").asText() : "-";
                        String type = snap.has("type") ? snap.get("type").asText() : "unknown";
                        int activeThreadCount = snap.has("activeThreadCount") ? snap.get("activeThreadCount").asInt() : 0;

                        ProcessorNodeEntity node = new ProcessorNodeEntity(procId, name, type);
                        node.setActiveThreadCount(activeThreadCount);
                        map.put(procId, node);
                    }
                }
            } catch (Exception e) {
                log.warn("Error processing PG {}: {}", id, e.getMessage());
            }
        }
        return map;
    }


    /**
     * Recursive helper method to identify all processors in the specified node
     * @param pgId - Id of the process group to be traversed
     * @param out - A list of all processors in that Process-group
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

    private void crawlProcessGroupHierarchy(String pgId, Map<String, ProcessGroupNodeEntity> pgMap) throws IOException {
        JsonNode pg = client.get(PG_ENDPOINT + pgId);
        if (pg == null || !pg.has("component")) return;

        String name = pg.get("component").has("name") ? pg.get("component").get("name").asText() : "-";
        ProcessGroupNodeEntity node = new ProcessGroupNodeEntity(pgId, name);
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
