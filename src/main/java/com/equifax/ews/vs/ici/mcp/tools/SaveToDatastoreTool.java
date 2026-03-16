package com.equifax.ews.vs.ici.mcp.tools;

import com.equifax.ews.vs.ici.mcp.McpTool;
import com.equifax.ews.vs.ici.model.AgentRequest;
import com.equifax.ews.vs.ici.service.DatastoreService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool for saving a request entry directly to Google Cloud Datastore
 * without triggering the full fulfillment workflow (no Pub/Sub publish).
 */
public class SaveToDatastoreTool implements McpTool {

    private final DatastoreService datastoreService;
    private final ObjectMapper objectMapper;

    public SaveToDatastoreTool(DatastoreService datastoreService, ObjectMapper objectMapper) {
        this.datastoreService = datastoreService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "save_to_datastore";
    }

    @Override
    public String getDescription() {
        return "Save a fulfillment request entry directly to Google Cloud Datastore without publishing to Pub/Sub. "
                + "Use this for persisting data when event notification is not needed.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("correlationId", Map.of("type", "string", "description", "Correlation ID for tracing"));
        properties.put("customerId", Map.of("type", "string", "description", "Customer identifier"));
        properties.put("orderId", Map.of("type", "string", "description", "Order identifier"));
        properties.put("requestType", Map.of("type", "string", "description", "Type of request"));
        properties.put("payload", Map.of("type", "object", "description", "Request payload data"));

        schema.put("properties", properties);
        schema.put("required", List.of("correlationId", "customerId", "orderId", "requestType", "payload"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        AgentRequest request = objectMapper.convertValue(arguments, AgentRequest.class);
        String requestId = datastoreService.saveRequestEntry(request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", requestId);
        result.put("status", "SAVED");
        result.put("message", "Request entry saved to Datastore");
        return result;
    }
}