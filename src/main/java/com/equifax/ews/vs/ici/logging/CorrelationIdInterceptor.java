package com.equifax.ews.vs.ici.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor for extracting and generating correlation IDs for request tracing.
 * Stores correlation ID in MDC for use throughout request processing.
 */
@Slf4j
@Component
public class CorrelationIdInterceptor implements HandlerInterceptor {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Extract or generate correlation ID
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = MDCUtil.generateId();
        }

        // Extract or generate request ID
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = MDCUtil.generateId();
        }

        // Store in MDC for downstream use
        MDCUtil.setCorrelationId(correlationId);
        MDCUtil.setRequestId(requestId);

        // Log request received
        log.info("Request received - method: {}, path: {}, correlationId: {}, requestId: {}",
                request.getMethod(), request.getRequestURI(), correlationId, requestId);

        // Add correlation ID to response headers
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String correlationId = MDCUtil.getCorrelationId();
        int status = response.getStatus();

        if (ex != null) {
            log.error("Request failed - correlationId: {}, status: {}, exception: {}",
                    correlationId, status, ex.getMessage(), ex);
        } else {
            log.info("Request completed - correlationId: {}, status: {}, path: {}",
                    correlationId, status, request.getRequestURI());
        }

        // Clear MDC after request
        MDCUtil.clear();
    }
}