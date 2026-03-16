package com.equifax.ews.vs.ici.mcp.tools;

import com.equifax.ews.vs.ici.mcp.McpTool;
import com.equifax.ews.vs.ici.service.PubSubService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP tool for publishing messages to Google Cloud Pub/Sub.
 * Supports publishing to the default fulfillment topic or a custom topic.
 */
public class PublishToPubSubTool implements McpTool {

    private final PubSubService pubSubService;
    private final ObjectMapper objectMapper;

    public PublishToPubSubTool(PubSubService pubSubService, ObjectMapper objectMapper) {
        this.pubSubService = pubSubService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "publish_to_pubsub";
    }

    @Override
    public String getDescription() {
        return "Publish a message to a Google Cloud Pub/Sub topic. Use this to notify downstream "
                + "agents or services about events. Includes correlation ID in message attributes for tracing.";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("topic", Map.of("type", "string", "description",
                "Pub/Sub topic name. If omitted, uses the default fulfillment-request-events topic."));
        properties.put("message", Map.of("type", "object", "description",
                "Message payload to publish (will be serialized to JSON)"));
        properties.put("attributes", Map.of("type", "object", "description",
                "Optional key-value attributes to attach to the Pub/Sub message"));

        schema.put("properties", properties);
        schema.put("required", List.of("message"));
        return schema;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> arguments) {
        Object message = arguments.get("message");
        if (message == null) {
            throw new IllegalArgumentException("message is required");
        }

        String topic = (String) arguments.get("topic");
        Map<String, String> attributes = null;

        Object rawAttributes = arguments.get("attributes");
        if (rawAttributes instanceof Map) {
            attributes = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) rawAttributes).entrySet()) {
                attributes.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        String messageId;
        if (topic != null && !topic.isBlank()) {
            messageId = pubSubService.publishMessage(topic, message, attributes);
        } else {
            messageId = pubSubService.publishMessage(
                    "fulfillment-request-events", message, attributes);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messageId", messageId);
        result.put("topic", topic != null ? topic : "fulfillment-request-events");
        result.put("status", "PUBLISHED");
        result.put("message", "Message published to Pub/Sub");
        return result;
    }
}