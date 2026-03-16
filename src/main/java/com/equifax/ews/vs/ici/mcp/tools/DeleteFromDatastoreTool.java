package com.equifax.ews.vs.ici.mcp.tools;

import com.equifax.ews.vs.ici.mcp.McpTool;
import com.equifax.ews.vs.ici.service.DatastoreService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool for deleting a request entry from Google Cloud Datastore by request ID.
 */
public class DeleteFromDatastoreTool implements McpTool {

    private final DatastoreService datastoreService;

    public DeleteFromDatastoreTool(DatastoreService datastoreService) {
        this.datastoreService = datastoreService;
    }

    @Override
    public String getName() {
        return "delete_from_datastore";
    }

    @Override
    public String getDescription() {
        return "Delete a fulfillment request entry from Google Cloud Datastore by its request ID. "
                + "This permanently removes the entry.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("requestId", Map.of("type", "string", "description", "The request ID of the entry to delete"));

        schema.put("properties", properties);
        schema.put("required", List.of("requestId"));
        return schema;
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        String requestId = (String) arguments.get("requestId");
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }

        datastoreService.deleteRequestEntry(requestId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", requestId);
        result.put("deleted", true);
        result.put("message", "Request entry deleted from Datastore");
        return result;
    }
}