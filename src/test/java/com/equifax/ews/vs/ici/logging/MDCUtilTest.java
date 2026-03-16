package com.equifax.ews.vs.ici.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

class MDCUtilTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void setCorrelationId_setsInMDC() {
        MDCUtil.setCorrelationId("corr-123");
        assertEquals("corr-123", MDC.get("correlationId"));
    }

    @Test
    void setCorrelationId_nullGeneratesId() {
        MDCUtil.setCorrelationId(null);
        String generated = MDC.get("correlationId");
        assertNotNull(generated);
        assertFalse(generated.isEmpty());
    }

    @Test
    void setCorrelationId_emptyGeneratesId() {
        MDCUtil.setCorrelationId("");
        String generated = MDC.get("correlationId");
        assertNotNull(generated);
        assertFalse(generated.isEmpty());
    }

    @Test
    void getCorrelationId_returnsSetValue() {
        MDCUtil.setCorrelationId("test-corr");
        assertEquals("test-corr", MDCUtil.getCorrelationId());
    }

    @Test
    void getCorrelationId_returnsNullWhenNotSet() {
        assertNull(MDCUtil.getCorrelationId());
    }

    @Test
    void setRequestId_setsInMDC() {
        MDCUtil.setRequestId("req-999");
        assertEquals("req-999", MDCUtil.getRequestId());
    }

    @Test
    void setRequestId_nullDoesNotSet() {
        MDCUtil.setRequestId(null);
        assertNull(MDCUtil.getRequestId());
    }

    @Test
    void setRequestId_emptyDoesNotSet() {
        MDCUtil.setRequestId("");
        assertNull(MDCUtil.getRequestId());
    }

    @Test
    void setPhase_setsInMDC() {
        MDCUtil.setPhase("VALIDATE");
        assertEquals("VALIDATE", MDC.get("phase"));
    }

    @Test
    void recordDuration_setsInMDC() {
        MDCUtil.recordDuration(42L);
        assertEquals("42", MDC.get("duration_ms"));
    }

    @Test
    void clear_removesAllMDCData() {
        MDCUtil.setCorrelationId("corr-1");
        MDCUtil.setRequestId("req-1");
        MDCUtil.setPhase("PHASE");
        MDCUtil.recordDuration(100L);

        MDCUtil.clear();

        assertNull(MDCUtil.getCorrelationId());
        assertNull(MDCUtil.getRequestId());
        assertNull(MDC.get("phase"));
        assertNull(MDC.get("duration_ms"));
    }

    @Test
    void generateId_returnsUniqueIds() {
        String id1 = MDCUtil.generateId();
        String id2 = MDCUtil.generateId();

        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
    }

    @Test
    void generateId_returnsUUIDFormat() {
        String id = MDCUtil.generateId();
        // UUID format: 8-4-4-4-12 hex characters
        assertTrue(id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }
}