package com.equifax.ews.vs.ici.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Input DTO for fulfillment request from customers.
 * Includes validation annotations for request parameter validation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentRequest {

    @NotNull(message = "Correlation ID cannot be null")
    @NotBlank(message = "Correlation ID cannot be blank")
    private String correlationId;

    @NotNull(message = "Customer ID cannot be null")
    @NotBlank(message = "Customer ID cannot be blank")
    private String customerId;

    @NotNull(message = "Order ID cannot be null")
    @NotBlank(message = "Order ID cannot be blank")
    private String orderId;

    @NotNull(message = "Request type cannot be null")
    @NotBlank(message = "Request type cannot be blank")
    private String requestType;

    @NotNull(message = "Payload cannot be null")
    private Object payload;

    private String userEmail;
    
    private String userPhone;

    private Long requestTimestamp;
}