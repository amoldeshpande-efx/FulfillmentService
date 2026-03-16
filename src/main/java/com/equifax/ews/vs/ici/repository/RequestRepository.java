package com.equifax.ews.vs.ici.repository;

import com.equifax.ews.vs.ici.entity.RequestEntry;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * Repository for RequestEntry using Google Cloud Datastore client.
 */
@Slf4j
@Repository
public class RequestRepository {

    private final Datastore datastore;
    private final ObjectMapper objectMapper;

    @Value("${app.datastore.kind:RequestEntry}")
    private String kind;

    public RequestRepository(Datastore datastore, ObjectMapper objectMapper) {
        this.datastore = datastore;
        this.objectMapper = objectMapper;
    }

    /**
     * Save RequestEntry to Datastore.
     */
    public RequestEntry save(RequestEntry entry) {
        try {
            KeyFactory keyFactory = datastore.newKeyFactory().setKind(kind);
            Key key = keyFactory.newKey(entry.getId());

            Entity entity = Entity.newBuilder(key)
                    .set("id", entry.getId())
                    .set("requestId", entry.getRequestId())
                    .set("correlationId", entry.getCorrelationId())
                    .set("customerId", entry.getCustomerId())
                    .set("orderId", entry.getOrderId())
                    .set("requestType", entry.getRequestType())
                    .set("requestPayload", entry.getRequestPayload())
                    .set("responsePayload", entry.getResponsePayload() != null ? entry.getResponsePayload() : "")
                    .set("status", entry.getStatus())
                    .set("messageId", entry.getMessageId() != null ? entry.getMessageId() : "")
                    .set("createdAt", entry.getCreatedAt())
                    .set("updatedAt", entry.getUpdatedAt())
                    .set("processingTimeMs", entry.getProcessingTimeMs() != null ? entry.getProcessingTimeMs() : 0L)
                    .set("errorCode", entry.getErrorCode() != null ? entry.getErrorCode() : "")
                    .set("errorMessage", entry.getErrorMessage() != null ? entry.getErrorMessage() : "")
                    .build();

            datastore.put(entity);
            return entry;
        } catch (Exception e) {
            log.error("Error saving RequestEntry - id: {}, error: {}", entry.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to save RequestEntry", e);
        }
    }

    /**
     * Find RequestEntry by ID.
     */
    public RequestEntry findByRequestId(String requestId) {
        try {
            KeyFactory keyFactory = datastore.newKeyFactory().setKind(kind);
            Key key = keyFactory.newKey(requestId);
            Entity entity = datastore.get(key);

            if (entity == null) {
                return null;
            }

            return mapEntityToRequestEntry(entity);
        } catch (Exception e) {
            log.error("Error finding RequestEntry - requestId: {}, error: {}", requestId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Delete RequestEntry by ID.
     */
    public void delete(RequestEntry entry) {
        try {
            KeyFactory keyFactory = datastore.newKeyFactory().setKind(kind);
            Key key = keyFactory.newKey(entry.getId());
            datastore.delete(key);
        } catch (Exception e) {
            log.error("Error deleting RequestEntry - id: {}, error: {}", entry.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to delete RequestEntry", e);
        }
    }

    /**
     * Map Datastore Entity to RequestEntry.
     */
    private RequestEntry mapEntityToRequestEntry(Entity entity) {
        return RequestEntry.builder()
                .id(entity.getString("id"))
                .requestId(entity.getString("requestId"))
                .correlationId(entity.getString("correlationId"))
                .customerId(entity.getString("customerId"))
                .orderId(entity.getString("orderId"))
                .requestType(entity.getString("requestType"))
                .requestPayload(entity.getString("requestPayload"))
                .responsePayload(entity.getString("responsePayload"))
                .status(entity.getString("status"))
                .messageId(entity.getString("messageId"))
                .createdAt(entity.getLong("createdAt"))
                .updatedAt(entity.getLong("updatedAt"))
                .processingTimeMs(entity.getLong("processingTimeMs"))
                .errorCode(entity.getString("errorCode"))
                .errorMessage(entity.getString("errorMessage"))
                .build();
    }
}