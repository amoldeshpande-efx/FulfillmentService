package com.equifax.ews.vs.ici.service;

import com.equifax.ews.vs.ici.model.AgentRequest;
import com.equifax.ews.vs.ici.model.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.Map;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FulfillmentServiceTest {

    @Mock
    private DatastoreService datastoreService;

    @Mock
    private PubSubService pubSubService;

    @Mock
    private EmailNotificationService emailNotificationService;

    @InjectMocks
    private FulfillmentService fulfillmentService;

    private AgentRequest validRequest;

    @BeforeEach
    void setUp() throws Exception {
        MDC.clear();

        // Set the @Value field that Mockito doesn't inject
        Field topicField = FulfillmentService.class.getDeclaredField("pubsubTopic");
        topicField.setAccessible(true);
        topicField.set(fulfillmentService, "fulfillment-request-events");

        validRequest = AgentRequest.builder()
                .correlationId("corr-123")
                .customerId("cust-456")
                .orderId("order-789")
                .requestType("ORDER")
                .payload(Map.of("item", "widget", "quantity", 5))
                .userEmail("test@equifax.com")
                .build();
    }

    @Test
    void processFulfillmentRequest_success() {
        when(datastoreService.saveRequestEntry(any(AgentRequest.class)))
                .thenReturn("req-id-001");
        when(pubSubService.publishMessage(anyString(), any(), anyMap()))
                .thenReturn("msg-id-001");

        AgentResponse response = fulfillmentService.processFulfillmentRequest(validRequest);

        assertNotNull(response);
        assertEquals("COMPLETED", response.getStatus());
        assertEquals("req-id-001", response.getRequestId());
        assertEquals("corr-123", response.getCorrelationId());
        assertEquals("Request successfully processed", response.getMessage());
        assertNotNull(response.getTimestamp());
        assertNotNull(response.getProcessingTimeMs());

        // Verify response data
        @SuppressWarnings("unchecked")
        Map<String, Object> responseData = (Map<String, Object>) response.getResponseData();
        assertEquals(true, responseData.get("submitted"));
        assertEquals("msg-id-001", responseData.get("pubsubMessageId"));

        verify(datastoreService).saveRequestEntry(validRequest);
        verify(pubSubService).publishMessage(anyString(), eq(validRequest), anyMap());
        verify(emailNotificationService).sendPublishNotification(
                eq("test@equifax.com"), eq("req-id-001"), eq("corr-123"),
                eq("msg-id-001"), anyString());
    }

    @Test
    void processFulfillmentRequest_validationError_missingCorrelationId() {
        AgentRequest badRequest = AgentRequest.builder()
                .customerId("cust-456")
                .orderId("order-789")
                .requestType("ORDER")
                .payload(Map.of("item", "widget"))
                .build();

        AgentResponse response = fulfillmentService.processFulfillmentRequest(badRequest);

        assertEquals("FAILED", response.getStatus());
        assertEquals("VALIDATION_ERROR", response.getErrorCode());
        assertTrue(response.getErrorMessage().contains("Correlation ID"));
        verifyNoInteractions(datastoreService, pubSubService, emailNotificationService);
    }

    @Test
    void processFulfillmentRequest_validationError_missingCustomerId() {
        AgentRequest badRequest = AgentRequest.builder()
                .correlationId("corr-123")
                .orderId("order-789")
                .requestType("ORDER")
                .payload(Map.of("item", "widget"))
                .build();

        AgentResponse response = fulfillmentService.processFulfillmentRequest(badRequest);

        assertEquals("FAILED", response.getStatus());
        assertEquals("VALIDATION_ERROR", response.getErrorCode());
        assertTrue(response.getErrorMessage().contains("Customer ID"));
    }

    @Test
    void processFulfillmentRequest_validationError_missingOrderId() {
        AgentRequest badRequest = AgentRequest.builder()
                .correlationId("corr-123")
                .customerId("cust-456")
                .requestType("ORDER")
                .payload(Map.of("item", "widget"))
                .build();

        AgentResponse response = fulfillmentService.processFulfillmentRequest(badRequest);

        assertEquals("FAILED", response.getStatus());
        assertEquals("VALIDATION_ERROR", response.getErrorCode());
        assertTrue(response.getErrorMessage().contains("Order ID"));
    }

    @Test
    void processFulfillmentRequest_validationError_missingRequestType() {
        AgentRequest badRequest = AgentRequest.builder()
                .correlationId("corr-123")
                .customerId("cust-456")
                .orderId("order-789")
                .payload(Map.of("item", "widget"))
                .build();

        AgentResponse response = fulfillmentService.processFulfillmentRequest(badRequest);

        assertEquals("FAILED", response.getStatus());
        assertEquals("VALIDATION_ERROR", response.getErrorCode());
        assertTrue(response.getErrorMessage().contains("Request type"));
    }

    @Test
    void processFulfillmentRequest_validationError_missingPayload() {
        AgentRequest badRequest = AgentRequest.builder()
                .correlationId("corr-123")
                .customerId("cust-456")
                .orderId("order-789")
                .requestType("ORDER")
                .build();

        AgentResponse response = fulfillmentService.processFulfillmentRequest(badRequest);

        assertEquals("FAILED", response.getStatus());
        assertEquals("VALIDATION_ERROR", response.getErrorCode());
        assertTrue(response.getErrorMessage().contains("Payload"));
    }

    @Test
    void processFulfillmentRequest_datastoreFailure_returnsInternalError() {
        when(datastoreService.saveRequestEntry(any(AgentRequest.class)))
                .thenThrow(new RuntimeException("Datastore unavailable"));

        AgentResponse response = fulfillmentService.processFulfillmentRequest(validRequest);

        assertEquals("FAILED", response.getStatus());
        assertEquals("INTERNAL_ERROR", response.getErrorCode());
        assertEquals("Internal error processing request", response.getErrorMessage());
        verify(pubSubService, never()).publishMessage(anyString(), any(), anyMap());
    }

    @Test
    void processFulfillmentRequest_pubsubFailure_returnsInternalError() {
        when(datastoreService.saveRequestEntry(any(AgentRequest.class)))
                .thenReturn("req-id-002");
        when(pubSubService.publishMessage(anyString(), any(), anyMap()))
                .thenThrow(new RuntimeException("Pub/Sub timeout"));

        AgentResponse response = fulfillmentService.processFulfillmentRequest(validRequest);

        assertEquals("FAILED", response.getStatus());
        assertEquals("INTERNAL_ERROR", response.getErrorCode());
    }

    @Test
    void processFulfillmentRequest_setsProcessingTimeMs() {
        when(datastoreService.saveRequestEntry(any())).thenReturn("req-id");
        when(pubSubService.publishMessage(anyString(), any(), anyMap())).thenReturn("msg-id");

        AgentResponse response = fulfillmentService.processFulfillmentRequest(validRequest);

        assertNotNull(response.getProcessingTimeMs());
        assertTrue(response.getProcessingTimeMs() >= 0);
    }
}