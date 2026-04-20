package com.guidinglight.nexusquant.adapter.okx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.model.OkxApiCredentials;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/**
 * OkxBootstrapFallbackFactory 创建仅用于启动兜底的 OKX adapter stub。
 * <p>
 * Why:
 * `nq-app` 作为 composition root 只应该决定“是否启用 fallback”，
 * 不能内联 OKX HTTP client/stub payload 等 adapter 细节。该工厂把 fallback policy 的实现细节收回
 * `nq-adapter-okx`，避免启动模块承载交易所方言。
 */
public final class OkxBootstrapFallbackFactory {

    private OkxBootstrapFallbackFactory() {
    }

    public static OkxExchangeAdapter create(RuntimeException bootstrapFailure) {
        ObjectMapper objectMapper = new ObjectMapper();
        Clock clock = Clock.systemUTC();
        OkxHttpClient publicStubClient = new OkxHttpClient(
                HttpClient.newHttpClient(),
                objectMapper,
                "http://127.0.0.1",
                Duration.ofSeconds(1),
                new OkxRequestSigner(),
                () -> "1970-01-01T00:00:00Z",
                new OkxApiCredentials("", "", ""),
                false
        ) {
            @Override
            public JsonNode get(String requestPathWithQuery, String traceId) {
                return buildStubInstrumentsPayload(objectMapper);
            }
        };
        OkxHttpClient authenticatedStubClient = new OkxHttpClient(
                HttpClient.newHttpClient(),
                objectMapper,
                "http://127.0.0.1",
                Duration.ofSeconds(1),
                new OkxRequestSigner(),
                () -> "1970-01-01T00:00:00Z",
                new OkxApiCredentials("", "", ""),
                false
        ) {
            @Override
            public JsonNode get(String requestPathWithQuery, String traceId) {
                throw disabledStubException(requestPathWithQuery, traceId, bootstrapFailure);
            }

            @Override
            public JsonNode post(String requestPath, String requestBodyJson, String traceId) {
                throw disabledStubException(requestPath, traceId, bootstrapFailure);
            }
        };
        OkxInstrumentsCache instrumentsCache = new OkxInstrumentsCache(publicStubClient, clock, Duration.ofDays(1));
        return new OkxExchangeAdapter(new OkxExchangeAdapter.Dependencies(
                objectMapper,
                authenticatedStubClient,
                instrumentsCache,
                clock
        ));
    }

    private static JsonNode buildStubInstrumentsPayload(ObjectMapper objectMapper) {
        var root = objectMapper.createObjectNode();
        root.put("code", "0");
        var data = root.putArray("data");
        var btcUsdt = data.addObject();
        btcUsdt.put("instId", "BTC-USDT");
        btcUsdt.put("tickSz", "0.01");
        btcUsdt.put("lotSz", "0.00000001");
        btcUsdt.put("minSz", "0.00000001");
        btcUsdt.put("state", "live");
        return root;
    }

    private static OkxApiException disabledStubException(
            String endpoint,
            String traceId,
            RuntimeException bootstrapFailure
    ) {
        return new OkxApiException(
                "OKX adapter bootstrap fallback active, endpoint=" + endpoint
                        + ", trace_id=" + traceId
                        + ", bootstrap_reason=" + bootstrapFailure.getMessage(),
                0,
                endpoint,
                "OKX_ADAPTER_BOOTSTRAP_STUB",
                traceId,
                bootstrapFailure
        );
    }
}
