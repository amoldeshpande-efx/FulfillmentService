package com.equifax.ews.vs.ici.controller;

import com.equifax.ews.vs.ici.exception.GlobalExceptionHandler;
import com.equifax.ews.vs.ici.model.AgentRequest;
import com.equifax.ews.vs.ici.model.AgentResponse;
import com.equifax.ews.vs.ici.service.FulfillmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FulfillmentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FulfillmentService fulfillmentService;

    @InjectMocks
    private FulfillmentController fulfillmentController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(fulfillmentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setValidator(validator)
                .build();
    }

    @Test
    void submitFulfillmentRequest_success() throws Exception {
        AgentResponse mockResponse = AgentResponse.builder()
                .requestId("req-001")
                .correlationId("corr-123")
                .status("COMPLETED")
                .message("Request successfully processed")
                .timestamp(System.currentTimeMillis())
                .processingTimeMs(100L)
                .build();

        when(fulfillmentService.processFulfillmentRequest(any(AgentRequest.class)))
                .thenReturn(mockResponse);

        String requestBody = objectMapper.writeValueAsString(AgentRequest.builder()
                .correlationId("corr-123")
                .customerId("cust-456")
                .orderId("order-789")
                .requestType("ORDER")
                .payload(Map.of("item", "widget"))
                .build());

        mockMvc.perform(post("/api/v1/fulfillment/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-001"))
                .andExpect(jsonPath("$.correlationId").value("corr-123"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.message").value("Request successfully processed"));

        verify(fulfillmentService).processFulfillmentRequest(any(AgentRequest.class));
    }

    @Test
    void submitFulfillmentRequest_validationError_missingRequiredFields() throws Exception {
        // Missing all required fields
        String requestBody = "{}";

        mockMvc.perform(post("/api/v1/fulfillment/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(fulfillmentService);
    }

    @Test
    void submitFulfillmentRequest_validationError_blankCorrelationId() throws Exception {
        String requestBody = objectMapper.writeValueAsString(AgentRequest.builder()
                .correlationId("")
                .customerId("cust-456")
                .orderId("order-789")
                .requestType("ORDER")
                .payload(Map.of("item", "widget"))
                .build());

        mockMvc.perform(post("/api/v1/fulfillment/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitFulfillmentRequest_validationError_nullPayload() throws Exception {
        String requestBody = """
                {
                    "correlationId": "corr-123",
                    "customerId": "cust-456",
                    "orderId": "order-789",
                    "requestType": "ORDER"
                }
                """;

        mockMvc.perform(post("/api/v1/fulfillment/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitFulfillmentRequest_serviceThrowsException() throws Exception {
        when(fulfillmentService.processFulfillmentRequest(any()))
                .thenThrow(new RuntimeException("Unexpected error"));

        String requestBody = objectMapper.writeValueAsString(AgentRequest.builder()
                .correlationId("corr-123")
                .customerId("cust-456")
                .orderId("order-789")
                .requestType("ORDER")
                .payload(Map.of("item", "widget"))
                .build());

        mockMvc.perform(post("/api/v1/fulfillment/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void healthCheck_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/fulfillment/health"))
                .andExpect(status().isOk());
    }

    @Test
    void submitFulfillmentRequest_invalidJson_returnsError() throws Exception {
        mockMvc.perform(post("/api/v1/fulfillment/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 400 || status == 500,
                            "Expected 400 or 500 but got " + status);
                });
    }
}