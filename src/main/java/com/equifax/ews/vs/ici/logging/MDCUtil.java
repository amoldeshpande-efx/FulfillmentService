package com.equifax.ews.vs.ici.logging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import java.util.UUID;

/**
 * Utility class for managing MDC (Mapped Diagnostic Context) across application layers.
 * Handles correlation ID and request ID propagation.
 */
@Slf4j
public class MDCUtil {

    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String PHASE_KEY = "phase";
    private static final String DURATION_KEY = "duration_ms";

    /**
     * Set correlation ID in MDC.
     */
    public static void setCorrelationId(String correlationId) {
        if (correlationId != null && !correlationId.isEmpty()) {
            MDC.put(CORRELATION_ID_KEY, correlationId);
        } else {
            MDC.put(CORRELATION_ID_KEY, generateId());
        }
    }

    /**
     * Get correlation ID from MDC.
     */
    public static String getCorrelationId() {
        return MDC.get(CORRELATION_ID_KEY);
    }

    /**
     * Set request ID in MDC.
     */
    public static void setRequestId(String requestId) {
        if (requestId != null && !requestId.isEmpty()) {
            MDC.put(REQUEST_ID_KEY, requestId);
        }
    }

    /**
     * Get request ID from MDC.
     */
    public static String getRequestId() {
        return MDC.get(REQUEST_ID_KEY);
    }

    /**
     * Set processing phase in MDC.
     */
    public static void setPhase(String phase) {
        MDC.put(PHASE_KEY, phase);
    }

    /**
     * Record processing duration in MDC.
     */
    public static void recordDuration(long durationMs) {
        MDC.put(DURATION_KEY, String.valueOf(durationMs));
    }

    /**
     * Clear all MDC data.
     */
    public static void clear() {
        MDC.clear();
    }

    /**
     * Generate a unique ID for correlation/request tracking.
     */
    public static String generateId() {
        return UUID.randomUUID().toString();
    }
}