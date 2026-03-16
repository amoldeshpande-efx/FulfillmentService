package com.equifax.ews.vs.ici.service;

import com.equifax.ews.vs.ici.logging.MDCUtil;
import com.equifax.ews.vs.ici.model.AgentRequest;
import com.equifax.ews.vs.ici.model.AgentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for orchestrating fulfillment business logic.
 * Coordinates: validate request -> save to datastore -> publish to pub/sub -> return response.
 */
@Slf4j
@Service
public class FulfillmentService {

    private final DatastoreService datastoreService;
    private final PubSubService pubSubService;
    private final EmailNotificationService emailNotificationService;

    @Value("${app.pubsub.topic:fulfillment-request-events}")
    private String pubsubTopic;

    public FulfillmentService(DatastoreService datastoreService, PubSubService pubSubService,
                              EmailNotificationService emailNotificationService) {
        this.datastoreService = datastoreService;
        this.pubSubService = pubSubService;
        this.emailNotificationService = emailNotificationService;
    }

    /**
     * Process a fulfillment request end-to-end.
     * Returns response with status and requestId.
     */
    public AgentResponse processFulfillmentRequest(AgentRequest request) {
        long overallStartTime = System.currentTimeMillis();
        String correlationId = request.getCorrelationId();
        String requestId = null;

        try {
            // Phase 1: Validate
            MDCUtil.setPhase("VALIDATE_REQUEST");
            long validateStart = System.currentTimeMillis();
            validateRequest(request);
            long validateDuration = System.currentTimeMillis() - validateStart;
            log.info("Request validation completed - duration_ms: {}, correlationId: {}",
                    validateDuration, correlationId);

            // Phase 2: Save to Datastore
            MDCUtil.setPhase("SAVE_TO_DATASTORE");
            long saveStart = System.currentTimeMillis();
            requestId = datastoreService.saveRequestEntry(request);
            long saveDuration = System.currentTimeMillis() - saveStart;
            log.info("Request saved to Datastore - requestId: {}, duration_ms: {}, correlationId: {}",
                    requestId, saveDuration, correlationId);

            // Phase 3: Publish to Pub/Sub
            MDCUtil.setPhase("PUBLISH_TO_PUBSUB");
            long publishStart = System.currentTimeMillis();
            Map<String, String> attributes = new HashMap<>();
            attributes.put("requestId", requestId);
            attributes.put("customerId", request.getCustomerId());
            attributes.put("orderId", request.getOrderId());
            String messageId = pubSubService.publishMessage(pubsubTopic, request, attributes);
            long publishDuration = System.currentTimeMillis() - publishStart;
            log.info("Request published to Pub/Sub - messageId: {}, duration_ms: {}, correlationId: {}",
                    messageId, publishDuration, correlationId);

            // Phase 3b: Send email notification (async, non-blocking)
            emailNotificationService.sendPublishNotification(
                    request.getUserEmail(), requestId, correlationId, messageId, pubsubTopic);

            // Phase 4: Build and return response
            MDCUtil.setPhase("BUILD_RESPONSE");
            long overallDuration = System.currentTimeMillis() - overallStartTime;

            AgentResponse response = AgentResponse.builder()
                    .requestId(requestId)
                    .correlationId(correlationId)
                    .status("COMPLETED")
                    .message("Request successfully processed")
                    .responseData(Map.of(
                            "submitted", true,
                            "processingStarted", true,
                            "pubsubMessageId", messageId
                    ))
                    .timestamp(System.currentTimeMillis())
                    .processingTimeMs(overallDuration)
                    .build();

            log.info("Fulfillment request processed successfully - requestId: {}, correlationId: {}, total_duration_ms: {}",
                    requestId, correlationId, overallDuration);

            return response;

        } catch (IllegalArgumentException e) {
            long overallDuration = System.currentTimeMillis() - overallStartTime;
            MDCUtil.recordDuration(overallDuration);
            log.warn("Validation error processing fulfillment request - correlationId: {}, error: {}",
                    correlationId, e.getMessage());

            return buildErrorResponse(requestId, correlationId, "VALIDATION_ERROR",
                    e.getMessage(), overallDuration);

        } catch (Exception e) {
            long overallDuration = System.currentTimeMillis() - overallStartTime;
            MDCUtil.recordDuration(overallDuration);
            log.error("Error processing fulfillment request - correlationId: {}, error: {}",
                    correlationId, e.getMessage(), e);

            return buildErrorResponse(requestId, correlationId, "INTERNAL_ERROR",
                    "Internal error processing request", overallDuration);
        }
    }

    /**
     * Validate incoming fulfillment request.
     */
    private void validateRequest(AgentRequest request) {
        if (request.getCorrelationId() == null || request.getCorrelationId().isEmpty()) {
            throw new IllegalArgumentException("Correlation ID is required");
        }
        if (request.getCustomerId() == null || request.getCustomerId().isEmpty()) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        if (request.getOrderId() == null || request.getOrderId().isEmpty()) {
            throw new IllegalArgumentException("Order ID is required");
        }
        if (request.getRequestType() == null || request.getRequestType().isEmpty()) {
            throw new IllegalArgumentException("Request type is required");
        }
        if (request.getPayload() == null) {
            throw new IllegalArgumentException("Payload is required");
        }
    }

    /**
     * Build error response.
     */
    private AgentResponse buildErrorResponse(String requestId, String correlationId,
                                            String errorCode, String errorMessage, long duration) {
        return AgentResponse.builder()
                .requestId(requestId)
                .correlationId(correlationId)
                .status("FAILED")
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .timestamp(System.currentTimeMillis())
                .processingTimeMs(duration)
                .build();
    }
}