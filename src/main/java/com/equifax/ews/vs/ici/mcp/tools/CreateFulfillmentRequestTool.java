package com.equifax.ews.vs.ici.mcp.tools;

import com.equifax.ews.vs.ici.mcp.McpTool;
import com.equifax.ews.vs.ici.model.AgentRequest;
import com.equifax.ews.vs.ici.model.AgentResponse;
import com.equifax.ews.vs.ici.service.FulfillmentService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool that runs the full fulfillment workflow:
 * validate → save to Datastore → publish to Pub/Sub → return response.
 */
public class CreateFulfillmentRequestTool implements McpTool {

    private final FulfillmentService fulfillmentService;
    private final ObjectMapper objectMapper;

    public CreateFulfillmentRequestTool(FulfillmentService fulfillmentService, ObjectMapper objectMapper) {
        this.fulfillmentService = fulfillmentService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "create_fulfillment_request";
    }

    @Override
    public String getDescription() {
        return "Create and process a new fulfillment request. Validates the request, saves it to "
                + "Google Cloud Datastore, publishes an event to Pub/Sub, and returns tracking information.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("correlationId", Map.of("type", "string", "description", "Unique correlation ID for end-to-end request tracing"));
        properties.put("customerId", Map.of("type", "string", "description", "Customer identifier"));
        properties.put("orderId", Map.of("type", "string", "description", "Order identifier"));
        properties.put("requestType", Map.of("type", "string", "description", "Type of fulfillment request (e.g., ORDER, RETURN, EXCHANGE)"));
        properties.put("payload", Map.of("type", "object", "description", "Request payload data containing fulfillment details"));
        properties.put("userEmail", Map.of("type", "string", "description", "Optional user email for notifications"));
        properties.put("userPhone", Map.of("type", "string", "description", "Optional user phone for notifications"));

        schema.put("properties", properties);
        schema.put("required", List.of("correlationId", "customerId", "orderId", "requestType", "payload"));
        return schema;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> arguments) {
        AgentRequest request = objectMapper.convertValue(arguments, AgentRequest.class);
        AgentResponse response = fulfillmentService.processFulfillmentRequest(request);
        return objectMapper.convertValue(response, Map.class);
    }
}