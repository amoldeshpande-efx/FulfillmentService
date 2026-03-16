package com.equifax.ews.vs.ici.mcp.tools;

import com.equifax.ews.vs.ici.entity.RequestEntry;
import com.equifax.ews.vs.ici.mcp.McpTool;
import com.equifax.ews.vs.ici.service.DatastoreService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool for reading a request entry from Google Cloud Datastore by request ID.
 */
public class ReadFromDatastoreTool implements McpTool {

    private final DatastoreService datastoreService;
    private final ObjectMapper objectMapper;

    public ReadFromDatastoreTool(DatastoreService datastoreService, ObjectMapper objectMapper) {
        this.datastoreService = datastoreService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "read_from_datastore";
    }

    @Override
    public String getDescription() {
        return "Read a fulfillment request entry from Google Cloud Datastore by its request ID. "
                + "Returns the full entry including status, payload, timestamps, and processing details.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("requestId", Map.of("type", "string", "description", "The unique request ID to look up"));

        schema.put("properties", properties);
        schema.put("required", List.of("requestId"));
        return schema;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> arguments) {
        String requestId = (String) arguments.get("requestId");
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }

        RequestEntry entry = datastoreService.getRequestEntry(requestId);
        if (entry == null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("found", false);
            result.put("requestId", requestId);
            result.put("message", "No entry found for the given request ID");
            return result;
        }

        Map<String, Object> result = objectMapper.convertValue(entry, Map.class);
        result.put("found", true);
        return result;
    }
}