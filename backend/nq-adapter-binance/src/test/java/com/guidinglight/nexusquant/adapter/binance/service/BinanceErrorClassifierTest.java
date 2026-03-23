package com.guidinglight.nexusquant.adapter.binance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.adapter.api.model.AdapterError;
import com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory;
import org.junit.jupiter.api.Test;

class BinanceErrorClassifierTest {

    @Test
    void shouldClassifyDeferredRetryableAndAuthFailures() {
        AdapterError deferred = BinanceErrorClassifier.toAdapterError(new BinanceApiException(
                "order not found",
                400,
                "/api/v3/order",
                "-2013",
                "Order does not exist.",
                "trc-binance-deferred"
        ));
        AdapterError retryable = BinanceErrorClassifier.toAdapterError(new BinanceApiException(
                "request timed out",
                0,
                "/api/v3/order",
                "HTTP_TIMEOUT",
                "request timed out",
                "trc-binance-timeout"
        ));
        AdapterError auth = BinanceErrorClassifier.toAdapterError(new BinanceApiException(
                "credentials missing",
                0,
                "/api/v3/order",
                "BINANCE_CREDENTIALS_MISSING",
                "credentials missing",
                "trc-binance-auth"
        ));

        assertEquals(AdapterResultCategory.DEFERRED, deferred.category());
        assertTrue(deferred.retryable());
        assertEquals(AdapterResultCategory.RETRYABLE_FAILURE, retryable.category());
        assertTrue(retryable.retryable());
        assertEquals(AdapterResultCategory.AUTH_FAILURE, auth.category());
    }
}
