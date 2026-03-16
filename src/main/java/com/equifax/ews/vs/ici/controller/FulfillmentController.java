package com.equifax.ews.vs.ici.controller;

import com.equifax.ews.vs.ici.logging.MDCUtil;
import com.equifax.ews.vs.ici.model.AgentRequest;
import com.equifax.ews.vs.ici.model.AgentResponse;
import com.equifax.ews.vs.ici.service.FulfillmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for fulfillment API endpoints.
 * Exposes POST endpoint for customers to submit fulfillment requests.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/fulfillment")
@Tag(name = "Fulfillment", description = "APIs for fulfillment request processing")
public class FulfillmentController {

    private final FulfillmentService fulfillmentService;

    public FulfillmentController(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    /**
     * Submit a fulfillment request.
     * Validates request, saves to Datastore, publishes to Pub/Sub, and returns response.
     */
    @PostMapping("/request")
    @Operation(
            summary = "Submit fulfillment request",
            description = "Submit a fulfillment request from customer system. Request is saved to Datastore and published to Pub/Sub for downstream processing."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Fulfillment request successfully processed",
                    content = @Content(schema = @Schema(implementation = AgentResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - validation error",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(ref = "#/components/schemas/ErrorResponse"))
            )
    })
    public ResponseEntity<AgentResponse> submitFulfillmentRequest(
            @Valid @RequestBody AgentRequest agentRequest) {

        try {
            // Set correlation ID from request
            String correlationId = agentRequest.getCorrelationId();
            MDCUtil.setCorrelationId(correlationId);

            log.info("Processing fulfillment request - correlationId: {}, customerId: {}, orderId: {}",
                    correlationId, agentRequest.getCustomerId(), agentRequest.getOrderId());

            // Process request through service layer
            AgentResponse response = fulfillmentService.processFulfillmentRequest(agentRequest);

            // Return 200 OK with response
            log.info("Fulfillment request completed successfully - requestId: {}, correlationId: {}",
                    response.getRequestId(), correlationId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Unexpected error in fulfillment controller - error: {}",
                    e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if fulfillment service is running")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Fulfillment service is running");
    }
}