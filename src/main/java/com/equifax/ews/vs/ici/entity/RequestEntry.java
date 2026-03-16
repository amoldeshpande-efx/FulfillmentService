package com.equifax.ews.vs.ici.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Datastore entity for storing fulfillment requests and responses.
 * Used with Google Cloud Datastore client (not Spring Data).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String requestId;
    private String correlationId;
    private String customerId;
    private String orderId;
    private String requestType;
    private String requestPayload;
    private String responsePayload;
    private String status;  // RECEIVED, PROCESSING, COMPLETED, FAILED
    private String messageId;  // Pub/Sub message ID
    private Long createdAt;
    private Long updatedAt;
    private Long processingTimeMs;
    private String errorCode;
    private String errorMessage;
}