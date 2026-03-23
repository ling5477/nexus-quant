package com.guidinglight.nexusquant.adapter.okx.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.adapter.api.model.AdapterError;
import com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory;
import org.junit.jupiter.api.Test;

class OkxErrorClassifierTest {

    @Test
    void shouldClassifyNotFoundThrottledAndAuthFailures() {
        AdapterError notFound = OkxErrorClassifier.toAdapterError(new OkxApiException(
                "order does not exist",
                200,
                "/api/v5/trade/order",
                "51603",
                OkxErrorCode.ORDER_NOT_FOUND,
                "trc-okx-not-found"
        ));
        AdapterError throttled = OkxErrorClassifier.toAdapterError(new OkxApiException(
                "rate limited",
                429,
                "/api/v5/trade/order",
                "50011",
                "trc-okx-throttled"
        ));
        AdapterError auth = OkxErrorClassifier.toAdapterError(new OkxApiException(
                "signature invalid",
                401,
                "/api/v5/trade/order",
                "50113",
                "trc-okx-auth"
        ));

        assertEquals(AdapterResultCategory.NOT_FOUND, notFound.category());
        assertEquals(AdapterResultCategory.THROTTLED, throttled.category());
        assertTrue(throttled.retryable());
        assertEquals(AdapterResultCategory.AUTH_FAILURE, auth.category());
    }
}
