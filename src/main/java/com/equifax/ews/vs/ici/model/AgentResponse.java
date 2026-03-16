package com.equifax.ews.vs.ici.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Output DTO for fulfillment response returned to customers.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentResponse {

    private String requestId;

    private String correlationId;

    private String status;

    private String message;

    private Object responseData;

    private Long timestamp;

    private Long processingTimeMs;

    private String errorCode;

    private String errorMessage;
}