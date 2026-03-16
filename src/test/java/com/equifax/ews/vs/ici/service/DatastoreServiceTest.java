package com.equifax.ews.vs.ici.service;

import com.equifax.ews.vs.ici.entity.RequestEntry;
import com.equifax.ews.vs.ici.model.AgentRequest;
import com.equifax.ews.vs.ici.model.AgentResponse;
import com.equifax.ews.vs.ici.repository.RequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatastoreServiceTest {

    @Mock
    private RequestRepository requestRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private DatastoreService datastoreService;

    private AgentRequest sampleRequest;

    @BeforeEach
    void setUp() {
        MDC.clear();
        sampleRequest = AgentRequest.builder()
                .correlationId("corr-100")
                .customerId("cust-200")
                .orderId("order-300")
                .requestType("ORDER")
                .payload(Map.of("item", "widget"))
                .build();
    }

    @Test
    void saveRequestEntry_success() {
        when(requestRepository.save(any(RequestEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        String requestId = datastoreService.saveRequestEntry(sampleRequest);

        assertNotNull(requestId);
        assertFalse(requestId.isEmpty());

        ArgumentCaptor<RequestEntry> captor = ArgumentCaptor.forClass(RequestEntry.class);
        verify(requestRepository).save(captor.capture());

        RequestEntry saved = captor.getValue();
        assertEquals(requestId, saved.getId());
        assertEquals(requestId, saved.getRequestId());
        assertEquals("corr-100", saved.getCorrelationId());
        assertEquals("cust-200", saved.getCustomerId());
        assertEquals("order-300", saved.getOrderId());
        assertEquals("ORDER", saved.getRequestType());
        assertEquals("RECEIVED", saved.getStatus());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
        assertNotNull(saved.getRequestPayload());
    }

    @Test
    void saveRequestEntry_repositoryThrows_propagatesException() {
        when(requestRepository.save(any(RequestEntry.class)))
                .thenThrow(new RuntimeException("Datastore down"));

        assertThrows(RuntimeException.class,
                () -> datastoreService.saveRequestEntry(sampleRequest));
    }

    @Test
    void updateRequestEntry_success() {
        RequestEntry existingEntry = RequestEntry.builder()
                .id("req-001")
                .requestId("req-001")
                .correlationId("corr-100")
                .customerId("cust-200")
                .orderId("order-300")
                .requestType("ORDER")
                .requestPayload("{\"item\":\"widget\"}")
                .status("RECEIVED")
                .createdAt(System.currentTimeMillis())
                .updatedAt(System.currentTimeMillis())
                .build();

        when(requestRepository.findByRequestId("req-001")).thenReturn(existingEntry);
        when(requestRepository.save(any(RequestEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AgentResponse response = AgentResponse.builder()
                .requestId("req-001")
                .status("COMPLETED")
                .responseData(Map.of("result", "processed"))
                .processingTimeMs(150L)
                .build();

        assertDoesNotThrow(() -> datastoreService.updateRequestEntry("req-001", response));

        ArgumentCaptor<RequestEntry> captor = ArgumentCaptor.forClass(RequestEntry.class);
        verify(requestRepository).save(captor.capture());

        RequestEntry updated = captor.getValue();
        assertEquals("COMPLETED", updated.getStatus());
        assertNotNull(updated.getResponsePayload());
        assertEquals(150L, updated.getProcessingTimeMs());
    }

    @Test
    void updateRequestEntry_notFound_throwsException() {
        when(requestRepository.findByRequestId("non-existent")).thenReturn(null);

        AgentResponse response = AgentResponse.builder()
                .status("COMPLETED")
                .build();

        assertThrows(RuntimeException.class,
                () -> datastoreService.updateRequestEntry("non-existent", response));
    }

    @Test
    void updateRequestEntry_withErrorFields() {
        RequestEntry existingEntry = RequestEntry.builder()
                .id("req-002")
                .requestId("req-002")
                .status("RECEIVED")
                .createdAt(System.currentTimeMillis())
                .updatedAt(System.currentTimeMillis())
                .build();

        when(requestRepository.findByRequestId("req-002")).thenReturn(existingEntry);
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AgentResponse response = AgentResponse.builder()
                .requestId("req-002")
                .status("FAILED")
                .errorCode("INTERNAL_ERROR")
                .errorMessage("Something broke")
                .processingTimeMs(50L)
                .build();

        datastoreService.updateRequestEntry("req-002", response);

        ArgumentCaptor<RequestEntry> captor = ArgumentCaptor.forClass(RequestEntry.class);
        verify(requestRepository).save(captor.capture());

        RequestEntry updated = captor.getValue();
        assertEquals("FAILED", updated.getStatus());
        assertEquals("INTERNAL_ERROR", updated.getErrorCode());
        assertEquals("Something broke", updated.getErrorMessage());
        assertNull(updated.getMessageId()); // Not COMPLETED, so no messageId
    }

    @Test
    void getRequestEntry_found() {
        RequestEntry entry = RequestEntry.builder()
                .id("req-010")
                .requestId("req-010")
                .status("COMPLETED")
                .build();

        when(requestRepository.findByRequestId("req-010")).thenReturn(entry);

        RequestEntry result = datastoreService.getRequestEntry("req-010");

        assertNotNull(result);
        assertEquals("req-010", result.getRequestId());
        assertEquals("COMPLETED", result.getStatus());
    }

    @Test
    void getRequestEntry_notFound_returnsNull() {
        when(requestRepository.findByRequestId("no-such-id")).thenReturn(null);

        RequestEntry result = datastoreService.getRequestEntry("no-such-id");

        assertNull(result);
    }

    @Test
    void getRequestEntry_repositoryThrows_propagatesException() {
        when(requestRepository.findByRequestId("bad-id"))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class,
                () -> datastoreService.getRequestEntry("bad-id"));
    }

    @Test
    void deleteRequestEntry_existingEntry() {
        RequestEntry entry = RequestEntry.builder()
                .id("req-del-01")
                .requestId("req-del-01")
                .build();

        when(requestRepository.findByRequestId("req-del-01")).thenReturn(entry);

        datastoreService.deleteRequestEntry("req-del-01");

        verify(requestRepository).delete(entry);
    }

    @Test
    void deleteRequestEntry_nonExistent_noDelete() {
        when(requestRepository.findByRequestId("no-entry")).thenReturn(null);

        datastoreService.deleteRequestEntry("no-entry");

        verify(requestRepository, never()).delete(any());
    }
}