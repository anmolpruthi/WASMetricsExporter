package com.score_me.was_metrics_exporter.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.score_me.was_metrics_exporter.client.FlowApiClient;
import com.score_me.was_metrics_exporter.entities.ProcessGroupNodeEntity;
import com.score_me.was_metrics_exporter.entities.ProcessorNodeEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GraphBuilder {
    private static final String PG_ENDPOINT = "/process-groups/";
    private final FlowApiClient client;

    public GraphBuilder(FlowApiClient client) {
        this.client = client;
    }

    private JsonNode getRootPg(String id) {
        return client.get(PG_ENDPOINT + id);
    }

    private void printProcessGroupError(String pgId, String errorMessage) {
        log.error("Error processing Process Group {}: {}", pgId, errorMessage);
    }

    /**
     * Method to build a map of all processors in the specified process group
     * This method will return a map of processor ID to ProcessorNodeEntity
     * where ProcessorNodeEntity contains the processor name, type, active thread count,
     * incoming connections, outgoing connections, and the process group ID
     * @param groupId
     * @return
     * @throws IOException
     */
    public Map<String, ProcessorNodeEntity> buildProcessorMap(String groupId) {
        Map<String, ProcessorNodeEntity> map = new HashMap<>();
        JsonNode root = getRootPg(groupId);
        List<String> pgIds = new ArrayList<>();
        crawlProcessGroups(root.get("id").asText(), pgIds);
        addProcessors(pgIds, map);
        addConnections(pgIds, map);
        return map;
    }

    /**
     * Method to build a map of all process groups in the specified process group
     * This method will return a map of process group ID to {@link ProcessGroupNodeEntity}
     * where {@link ProcessGroupNodeEntity} contains the process group name and a list of child process group IDs
     * This method will traverse the process group hierarchy and build a map of all process groups
     * @param groupId
     * @return Map of process group ID to ProcessGroupNodeEntity
     * where ProcessGroupNodeEntity contains the process group name and a list of child process group IDs
     * The map will include the root process group and all its descendants
     * @throws IOException
     */
    public Map<String, ProcessGroupNodeEntity> buildProcessGroupMap(String groupId) throws IOException {
        Map<String, ProcessGroupNodeEntity> pgMap = new HashMap<>();
        JsonNode root = getRootPg(groupId);
        String rootId = root.get("id").asText();
        crawlProcessGroupHierarchy(rootId, pgMap);
        return pgMap;
    }

    /**
     * Method to get the input or output ports of a process group
     * This method will return a map of port ID to port name for the specified port type
     * @param portType : "input" or "output"
     * @param groupId
     * @return
     * @throws IOException
     */
    public int getPortCount(String portType, String groupId) throws IOException {

        String portEndpoint;
        String fieldName;

        if (portType.equals("input")) {
            portEndpoint = "/input-ports";
        } else if (portType.equals("output")) {
            portEndpoint = "/output-ports";
        } else {
            throw new IOException("Invalid port type");
        }
        if (portType.equals("input")) {
            fieldName = "inputPorts";
        } else if (portType.equals("output")) {
            fieldName = "outputPorts";
        } else {
            throw new IOException("Invalid port type");
        }

        JsonNode root = getRootPg(groupId);
        if (root == null || !root.has("id")) {
            throw new IOException("Failed to fetch root PG");
        }


        List<String> pgIds = new ArrayList<>();
        crawlProcessGroups(root.get("id").asText(), pgIds);
        Map<String, String> portMap = buildPortMap(pgIds, fieldName, portEndpoint);
        return portMap.size();
    }

    /**
     * Method to build a map of ports for the specified process groups
     * This method will return a map of port ID to port name for the specified process groups
     * @return Map of port ID to port name
     * @param pgIds
     * @param fieldName
     * @param portEndpoint
     * @return
     */
    private Map<String, String> buildPortMap(List<String> pgIds, String fieldName, String portEndpoint) {
        Map<String, String> portMap = new HashMap<>();
        for (String pgId : pgIds) {
            try {
                JsonNode ports = client.get(PG_ENDPOINT + pgId + portEndpoint);
                if (ports != null && ports.has(fieldName)) {
                    for (JsonNode portName : ports.get(fieldName)) {
                        String id = portName.get("id").asText();
                        String name = portName.get("component").get("name").asText();
                        portMap.put(id, name);
                    }
                }
            } catch (Exception e) {
                printProcessGroupError(pgId, e.getMessage());
            }
        }
        return  portMap;
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

    /**
     * Recursive method to build a hierarchy of process groups
     * This method will traverse the process group tree and build a map of {@link ProcessGroupNodeEntity}
     * where the key is the process group ID and the value is the {@link ProcessGroupNodeEntity}
     * @param pgId
     * @param pgMap
     * @throws IOException
     */
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
                    crawlProcessGroupHierarchy(childId, pgMap);
                }
            }
        }
    }


    /**
     * Method to add processors to the processor map
     * This method will traverse the process group hierarchy and build a map of {@link ProcessorNodeEntity}
     * where the key is the processor ID and the value is the {@link ProcessorNodeEntity}.
     * This method will also add the active thread count for each processor
     * It will also add the incoming and outgoing connections for each processor
     * @param pgIds
     * @param processorNodeEntityMap
     */
    private void addProcessors(List<String> pgIds, Map<String, ProcessorNodeEntity> processorNodeEntityMap) {
        for (String pgId : pgIds) {
            try{
                JsonNode procs = client.get(PG_ENDPOINT + pgId + "/processors");
                if (procs != null && procs.has("processors")) {
                    for (JsonNode p : procs.get("processors")) {
                        JsonNode status = p.get("status");
                        if (status == null || !status.has("aggregateSnapshot")) continue;
                        JsonNode snap = status.get("aggregateSnapshot");
                        String id = snap.has("id") ? snap.get("id").asText() : null;
                        String name = snap.has("name") ? snap.get("name").asText() : "-";
                        String type = snap.has("type") ? snap.get("type").asText() : "unknown";
                        int activeThreadCount = snap.has("activeThreadCount") ? snap.get("activeThreadCount").asInt() : 0;

                        ProcessorNodeEntity node = new ProcessorNodeEntity(id, name, type);
                        node.setActiveThreadCount(activeThreadCount);
                        processorNodeEntityMap.put(id, node);
                    }
                }
            }
            catch(Exception e){
                log.warn("Error processing PG {}: {}", pgId, e.getMessage());
            }
        }
    }

    /**
     * Method to add connections between processors in the process groups
     * This method will traverse the process group hierarchy and build a map of connections
     * where the key is the source processor ID and the value is a list of destination processor
     * @param pgIds
     * @param processorNodeEntityMap of type
     */
    private void addConnections(List<String> pgIds, Map<String, ProcessorNodeEntity> processorNodeEntityMap) {
        for (String pgId : pgIds) {
            try{
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
                            processorNodeEntityMap.computeIfAbsent(srcId, id -> new ProcessorNodeEntity(id, "unknown-src", "unknown"));
                            processorNodeEntityMap.computeIfAbsent(dstId, id -> new ProcessorNodeEntity(id, "unknown-dst", "unknown"));
                            processorNodeEntityMap.get(srcId).getOutgoing().add(dstId);
                            processorNodeEntityMap.get(dstId).getIncoming().add(srcId);
                        }
                    }
                }
            }
            catch (Exception e){
                printProcessGroupError(pgId, e.getMessage());
            }
        }
    }


}
