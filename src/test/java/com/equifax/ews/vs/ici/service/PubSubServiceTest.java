package com.equifax.ews.vs.ici.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.google.pubsub.v1.PubsubMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PubSubServiceTest {

    @Mock
    private PubSubTemplate pubSubTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PubSubService pubSubService;

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @Test
    void publishMessage_success() throws Exception {
        CompletableFuture<String> future = CompletableFuture.completedFuture("msg-123");
        when(pubSubTemplate.publish(eq("test-topic"), any(PubsubMessage.class)))
                .thenReturn(future);

        Map<String, String> attributes = Map.of("key1", "value1");
        String messageId = pubSubService.publishMessage("test-topic", Map.of("data", "test"), attributes);

        assertEquals("msg-123", messageId);

        ArgumentCaptor<PubsubMessage> captor = ArgumentCaptor.forClass(PubsubMessage.class);
        verify(pubSubTemplate).publish(eq("test-topic"), captor.capture());

        PubsubMessage published = captor.getValue();
        assertNotNull(published.getData());
        assertTrue(published.getAttributesMap().containsKey("key1"));
        assertTrue(published.getAttributesMap().containsKey("X-Correlation-ID"));
        assertTrue(published.getAttributesMap().containsKey("X-Request-ID"));
        assertTrue(published.getAttributesMap().containsKey("timestamp"));
    }

    @Test
    void publishMessage_withCorrelationIdInMDC() throws Exception {
        MDC.put("correlationId", "corr-abc");
        MDC.put("requestId", "req-xyz");

        CompletableFuture<String> future = CompletableFuture.completedFuture("msg-456");
        when(pubSubTemplate.publish(anyString(), any(PubsubMessage.class)))
                .thenReturn(future);

        pubSubService.publishMessage("topic", Map.of("hello", "world"), null);

        ArgumentCaptor<PubsubMessage> captor = ArgumentCaptor.forClass(PubsubMessage.class);
        verify(pubSubTemplate).publish(eq("topic"), captor.capture());

        Map<String, String> attrs = captor.getValue().getAttributesMap();
        assertEquals("corr-abc", attrs.get("X-Correlation-ID"));
        assertEquals("req-xyz", attrs.get("X-Request-ID"));
    }

    @Test
    void publishMessage_nullAttributes() throws Exception {
        CompletableFuture<String> future = CompletableFuture.completedFuture("msg-789");
        when(pubSubTemplate.publish(anyString(), any(PubsubMessage.class)))
                .thenReturn(future);

        String messageId = pubSubService.publishMessage("topic", "simple string", null);

        assertEquals("msg-789", messageId);
        verify(pubSubTemplate).publish(eq("topic"), any(PubsubMessage.class));
    }

    @Test
    void publishMessage_singleArgOverload() throws Exception {
        CompletableFuture<String> future = CompletableFuture.completedFuture("msg-999");
        when(pubSubTemplate.publish(anyString(), any(PubsubMessage.class)))
                .thenReturn(future);

        String messageId = pubSubService.publishMessage("my-topic", Map.of("k", "v"));

        assertEquals("msg-999", messageId);
    }

    @Test
    void publishMessage_futureThrows_propagatesException() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Pub/Sub unavailable"));
        when(pubSubTemplate.publish(anyString(), any(PubsubMessage.class)))
                .thenReturn(future);

        assertThrows(RuntimeException.class,
                () -> pubSubService.publishMessage("topic", Map.of("data", "fail"), null));
    }

    @Test
    void publishMessage_serializationError_throwsException() throws Exception {
        // Create an object that causes serialization failure
        ObjectMapper failMapper = mock(ObjectMapper.class);
        when(failMapper.writeValueAsString(any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("bad") {});

        PubSubService serviceWithBadMapper = new PubSubService(pubSubTemplate, failMapper);

        assertThrows(RuntimeException.class,
                () -> serviceWithBadMapper.publishMessage("topic", new Object(), null));
    }

    @Test
    void publishMessage_messageDataContainsPayload() throws Exception {
        CompletableFuture<String> future = CompletableFuture.completedFuture("msg-data");
        when(pubSubTemplate.publish(anyString(), any(PubsubMessage.class)))
                .thenReturn(future);

        Map<String, Object> payload = Map.of("orderId", "O-100", "amount", 42.5);
        pubSubService.publishMessage("topic", payload, null);

        ArgumentCaptor<PubsubMessage> captor = ArgumentCaptor.forClass(PubsubMessage.class);
        verify(pubSubTemplate).publish(anyString(), captor.capture());

        String data = captor.getValue().getData().toStringUtf8();
        assertTrue(data.contains("O-100"));
        assertTrue(data.contains("42.5"));
    }
}