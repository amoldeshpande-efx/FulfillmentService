package com.equifax.ews.vs.ici.service;

import com.equifax.ews.vs.ici.entity.RequestEntry;
import com.equifax.ews.vs.ici.logging.MDCUtil;
import com.equifax.ews.vs.ici.model.AgentRequest;
import com.equifax.ews.vs.ici.model.AgentResponse;
import com.equifax.ews.vs.ici.repository.RequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for Datastore operations specific to RequestEntry entity.
 */
@Slf4j
@Service
public class DatastoreService {

    private final RequestRepository requestRepository;
    private final ObjectMapper objectMapper;

    public DatastoreService(RequestRepository requestRepository, ObjectMapper objectMapper) {
        this.requestRepository = requestRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Save a new request entry to Datastore.
     */
    public String saveRequestEntry(AgentRequest request) {
        long startTime = System.currentTimeMillis();
        String correlationId = MDCUtil.getCorrelationId();

        try {
            String requestId = UUID.randomUUID().toString();
            MDCUtil.setRequestId(requestId);

            long currentTime = System.currentTimeMillis();
            String payloadJson = objectMapper.writeValueAsString(request.getPayload());

            RequestEntry entry = RequestEntry.builder()
                    .id(requestId)
                    .requestId(requestId)
                    .correlationId(request.getCorrelationId())
                    .customerId(request.getCustomerId())
                    .orderId(request.getOrderId())
                    .requestType(request.getRequestType())
                    .requestPayload(payloadJson)
                    .status("RECEIVED")
                    .createdAt(currentTime)
                    .updatedAt(currentTime)
                    .build();

            RequestEntry savedEntry = requestRepository.save(entry);

            long duration = System.currentTimeMillis() - startTime;
            MDCUtil.recordDuration(duration);

            log.info("Request entry saved to Datastore - requestId: {}, correlationId: {}, duration_ms: {}",
                    requestId, correlationId, duration);

            return requestId;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            MDCUtil.recordDuration(duration);
            log.error("Failed to save request entry to Datastore - correlationId: {}, error: {}",
                    correlationId, e.getMessage(), e);
            throw new RuntimeException("Failed to save request to Datastore", e);
        }
    }

    /**
     * Update request entry with response data.
     */
    public void updateRequestEntry(String requestId, AgentResponse response) {
        long startTime = System.currentTimeMillis();
        String correlationId = MDCUtil.getCorrelationId();

        try {
            MDCUtil.setPhase("UPDATE_DATASTORE");

            RequestEntry entry = requestRepository.findByRequestId(requestId);
            if (entry == null) {
                throw new RuntimeException("Request entry not found: " + requestId);
            }

            String responseJson = objectMapper.writeValueAsString(response.getResponseData());

            entry.setResponsePayload(responseJson);
            entry.setStatus(response.getStatus());
            entry.setUpdatedAt(System.currentTimeMillis());
            entry.setProcessingTimeMs(response.getProcessingTimeMs());
            entry.setMessageId(response.getStatus().equals("COMPLETED") ? MDCUtil.generateId() : null);

            if (response.getErrorCode() != null) {
                entry.setErrorCode(response.getErrorCode());
                entry.setErrorMessage(response.getErrorMessage());
            }

            requestRepository.save(entry);

            long duration = System.currentTimeMillis() - startTime;
            MDCUtil.recordDuration(duration);

            log.info("Request entry updated in Datastore - requestId: {}, status: {}, correlationId: {}, duration_ms: {}",
                    requestId, response.getStatus(), correlationId, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            MDCUtil.recordDuration(duration);
            log.error("Failed to update request entry - requestId: {}, correlationId: {}, error: {}",
                    requestId, correlationId, e.getMessage(), e);
            throw new RuntimeException("Failed to update request in Datastore", e);
        }
    }

    /**
     * Retrieve request entry by ID.
     */
    public RequestEntry getRequestEntry(String requestId) {
        long startTime = System.currentTimeMillis();
        String correlationId = MDCUtil.getCorrelationId();

        try {
            RequestEntry entry = requestRepository.findByRequestId(requestId);

            long duration = System.currentTimeMillis() - startTime;
            log.debug("Retrieved request entry - requestId: {}, found: {}, duration_ms: {}",
                    requestId, entry != null, duration);

            return entry;
        } catch (Exception e) {
            log.error("Error retrieving request entry - requestId: {}, correlationId: {}, error: {}",
                    requestId, correlationId, e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve request from Datastore", e);
        }
    }

    /**
     * Delete request entry by ID.
     */
    public void deleteRequestEntry(String requestId) {
        long startTime = System.currentTimeMillis();
        String correlationId = MDCUtil.getCorrelationId();

        try {
            RequestEntry entry = requestRepository.findByRequestId(requestId);
            if (entry != null) {
                requestRepository.delete(entry);
                long duration = System.currentTimeMillis() - startTime;
                log.info("Request entry deleted from Datastore - requestId: {}, correlationId: {}, duration_ms: {}",
                        requestId, correlationId, duration);
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to delete request entry - requestId: {}, correlationId: {}, error: {}",
                    requestId, correlationId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete request from Datastore", e);
        }
    }
}