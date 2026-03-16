package com.equifax.ews.vs.ici.service;

import com.equifax.ews.vs.ici.logging.MDCUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.pubsub.v1.PubsubMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Service for publishing messages to Google Cloud Pub/Sub.
 * Includes correlation ID in message attributes for tracing.
 */
@Slf4j
@Service
public class PubSubService {

    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.pubsub.topic:fulfillment-request-events}")
    private String defaultTopic;

    public PubSubService(PubSubTemplate pubSubTemplate, ObjectMapper objectMapper) {
        this.pubSubTemplate = pubSubTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publish a message to Pub/Sub with correlation ID in attributes.
     */
    public String publishMessage(String topic, Object message) {
        return publishMessage(topic, message, null);
    }

    /**
     * Publish a message to Pub/Sub with custom attributes including correlation ID.
     */
    public String publishMessage(String topic, Object message, Map<String, String> customAttributes) {
        long startTime = System.currentTimeMillis();
        String correlationId = MDCUtil.getCorrelationId();
        String requestId = MDCUtil.getRequestId();

        try {
            // Convert message to JSON
            String jsonMessage = objectMapper.writeValueAsString(message);

            // Build attributes with correlation ID
            Map<String, String> attributes = new HashMap<>();
            if (customAttributes != null) {
                attributes.putAll(customAttributes);
            }
            attributes.put("X-Correlation-ID", correlationId != null ? correlationId : "");
            attributes.put("X-Request-ID", requestId != null ? requestId : "");
            attributes.put("timestamp", String.valueOf(System.currentTimeMillis()));

            // Build and publish message
            PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
                    .setData(com.google.protobuf.ByteString.copyFromUtf8(jsonMessage))
                    .putAllAttributes(attributes)
                    .build();

            String messageId = pubSubTemplate.publish(topic, pubsubMessage).get();

            long duration = System.currentTimeMillis() - startTime;
            MDCUtil.recordDuration(duration);

            log.info("Message published to Pub/Sub - topic: {}, messageId: {}, correlationId: {}, duration_ms: {}",
                    topic, messageId, correlationId, duration);

            return messageId;
        } catch (ExecutionException | InterruptedException e) {
            long duration = System.currentTimeMillis() - startTime;
            MDCUtil.recordDuration(duration);
            log.error("Failed to publish message to Pub/Sub - topic: {}, correlationId: {}, error: {}",
                    topic, correlationId, e.getMessage(), e);
            throw new RuntimeException("Failed to publish message to Pub/Sub", e);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            MDCUtil.recordDuration(duration);
            log.error("Error publishing message - correlationId: {}, error: {}",
                    correlationId, e.getMessage(), e);
            throw new RuntimeException("Error serializing message for Pub/Sub", e);
        }
    }

    /**
     * Publish to default topic.
     */
    public String publishToDefaultTopic(Object message) {
        return publishMessage(defaultTopic, message);
    }

    /**
     * Publish with custom topic.
     */
    public String publishToCustomTopic(String topic, Object message, Map<String, String> attributes) {
        return publishMessage(topic, message, attributes);
    }
}