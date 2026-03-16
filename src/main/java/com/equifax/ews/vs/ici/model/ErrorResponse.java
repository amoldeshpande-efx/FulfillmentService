package com.equifax.ews.vs.ici.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard error response for API errors.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private String errorCode;

    private String message;

    private String details;

    private String correlationId;

    private Long timestamp;

    private Integer httpStatus;

    private String path;

    private String traceId;
}