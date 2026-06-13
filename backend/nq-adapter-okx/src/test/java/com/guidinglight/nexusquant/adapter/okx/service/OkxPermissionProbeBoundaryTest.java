package com.guidinglight.nexusquant.adapter.okx.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OkxPermissionProbeBoundaryTest {

    @Test
    void shouldClassifyProbeErrorsWithoutRawResponse() {
        assertEquals("TIMEOUT", OkxPermissionProbeBoundary.classify(0, "HTTP_TIMEOUT"));
        assertEquals("RATE_LIMITED", OkxPermissionProbeBoundary.classify(429, "50011"));
        assertEquals("EXCHANGE_5XX", OkxPermissionProbeBoundary.classify(500, "50000"));
        assertEquals("AUTH_FAILED", OkxPermissionProbeBoundary.classify(401, "50113"));
        assertEquals("IP_ALLOWLIST_FAILED", OkxPermissionProbeBoundary.classify(403, "50035"));
    }

    @Test
    void shouldForbidTradingAndFundingEndpoints() {
        assertTrue(OkxPermissionProbeBoundary.isForbiddenEndpoint("/api/v5/trade/order"));
        assertTrue(OkxPermissionProbeBoundary.isForbiddenEndpoint("/api/v5/trade/cancel-order"));
        assertTrue(OkxPermissionProbeBoundary.isForbiddenEndpoint("/api/v5/asset/withdrawal"));
        assertTrue(OkxPermissionProbeBoundary.isForbiddenEndpoint("/api/v5/asset/transfer"));
        assertFalse(OkxPermissionProbeBoundary.isForbiddenEndpoint("/api/v5/account/config"));
    }
}
