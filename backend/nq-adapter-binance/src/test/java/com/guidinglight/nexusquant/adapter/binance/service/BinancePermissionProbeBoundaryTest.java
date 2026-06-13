package com.guidinglight.nexusquant.adapter.binance.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinancePermissionProbeBoundaryTest {

    @Test
    void shouldClassifyProbeErrorsWithoutRawResponse() {
        assertEquals("TIMEOUT", BinancePermissionProbeBoundary.classify(0, "HTTP_TIMEOUT"));
        assertEquals("RATE_LIMITED", BinancePermissionProbeBoundary.classify(429, "-1003"));
        assertEquals("EXCHANGE_5XX", BinancePermissionProbeBoundary.classify(500, "HTTP_CLIENT_ERROR"));
        assertEquals("AUTH_FAILED", BinancePermissionProbeBoundary.classify(401, "-2015"));
        assertEquals("IP_ALLOWLIST_FAILED", BinancePermissionProbeBoundary.classify(403, "IP_RESTRICTED"));
    }

    @Test
    void shouldForbidTradingAndFundingEndpoints() {
        assertTrue(BinancePermissionProbeBoundary.isForbiddenEndpoint("POST", "/api/v3/order"));
        assertTrue(BinancePermissionProbeBoundary.isForbiddenEndpoint("DELETE", "/api/v3/order"));
        assertTrue(BinancePermissionProbeBoundary.isForbiddenEndpoint("POST", "/sapi/v1/asset/transfer"));
        assertTrue(BinancePermissionProbeBoundary.isForbiddenEndpoint("POST", "/sapi/v1/capital/withdraw/apply"));
        assertFalse(BinancePermissionProbeBoundary.isForbiddenEndpoint("GET", "/api/v3/account"));
    }
}
