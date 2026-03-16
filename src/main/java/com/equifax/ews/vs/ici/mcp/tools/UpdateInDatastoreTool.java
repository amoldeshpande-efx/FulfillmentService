package com.equifax.ews.vs.ici.mcp.tools;

import com.equifax.ews.vs.ici.mcp.McpTool;
import com.equifax.ews.vs.ici.model.AgentResponse;
import com.equifax.ews.vs.ici.service.DatastoreService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool for updating an existing request entry in Google Cloud Datastore.
 * Updates status, response data, processing time, and error information.
 */
public class UpdateInDatastoreTool implements McpTool {

    private final DatastoreService datastoreService;
    private final ObjectMapper objectMapper;

    public UpdateInDatastoreTool(DatastoreService datastoreService, ObjectMapper objectMapper) {
        this.datastoreService = datastoreService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "update_in_datastore";
    }

    @Override
    public String getDescription() {
        return "Update an existing fulfillment request entry in Google Cloud Datastore. "
                + "Use this to update status, add response data, or record errors for a previously saved request.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("requestId", Map.of("type", "string", "description", "The request ID of the entry to update"));
        properties.put("status", Map.of("type", "string", "description", "New status (RECEIVED, PROCESSING, COMPLETED, FAILED)"));
        properties.put("responseData", Map.of("type", "object", "description", "Response data to attach to the entry"));
        properties.put("processingTimeMs", Map.of("type", "integer", "description", "Processing time in milliseconds"));
        properties.put("errorCode", Map.of("type", "string", "description", "Error code if status is FAILED"));
        properties.put("errorMessage", Map.of("type", "string", "description", "Error message if status is FAILED"));

        schema.put("properties", properties);
        schema.put("required", List.of("requestId", "status"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        String requestId = (String) arguments.get("requestId");
        String status = (String) arguments.get("status");

        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }

        AgentResponse response = AgentResponse.builder()
                .requestId(requestId)
                .status(status)
                .responseData(arguments.get("responseData"))
                .processingTimeMs(arguments.get("processingTimeMs") != null
                        ? ((Number) arguments.get("processingTimeMs")).longValue() : null)
                .errorCode((String) arguments.get("errorCode"))
                .errorMessage((String) arguments.get("errorMessage"))
                .build();

        datastoreService.updateRequestEntry(requestId, response);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", requestId);
        result.put("status", status);
        result.put("message", "Request entry updated in Datastore");
        return result;
    }
}