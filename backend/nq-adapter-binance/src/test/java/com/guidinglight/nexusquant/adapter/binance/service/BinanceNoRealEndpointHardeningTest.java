package com.guidinglight.nexusquant.adapter.binance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;

import java.net.http.HttpClient;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * BinanceNoRealEndpointHardeningTest 固定 GateL-1B-A 的 no-real / no-outbound 默认边界。
 * <p>
 * Why:
 * GateL-1 review 把“Binance 默认 endpoint 仍指向 testnet/mainnet”记为 P1-A。
 * 本测试证明：默认 REST/WS endpoint 为 `disabled://` no-real sentinel，且在 sentinel 默认下，
 * REST 请求在到达网络之前即 loud fail-closed（非法 scheme 抛 {@link IllegalArgumentException}），
 * 不会构造真实网络 endpoint，也不会发生 outbound。本测试只在本地执行，不访问任何外部服务。
 */
class BinanceNoRealEndpointHardeningTest {

    @Test
    void shouldDefaultRestAndWsEndpointsToNonRealSentinels() {
        BinanceRuntimeConfig dome = BinanceRuntimeConfig.fromEnvironment(Map.of());
        BinanceRuntimeConfig real = BinanceRuntimeConfig.fromEnvironment(Map.of("NQ_BINANCE_ENV", "real"));

        for (BinanceRuntimeConfig config : new BinanceRuntimeConfig[] {dome, real}) {
            assertEquals("disabled://binance-not-configured", config.baseUrl());
            assertEquals("disabled://binance-ws-not-configured", config.wsUrl());
            for (String endpoint : new String[] {config.baseUrl(), config.wsUrl()}) {
                assertFalse(endpoint.contains("binance.com"), () -> "default endpoint must not contain binance.com: " + endpoint);
                assertFalse(endpoint.contains("binance.vision"), () -> "default endpoint must not contain binance.vision: " + endpoint);
            }
        }
    }

    @Test
    void shouldFailClosedWithoutOutboundWhenBaseUrlIsDisabledSentinel() {
        BinanceRuntimeConfig config = BinanceRuntimeConfig.fromEnvironment(Map.of());
        BinanceHttpClient client = new BinanceHttpClient(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                config.baseUrl(),
                config.timeout(),
                new BinanceRequestSigner(),
                System::currentTimeMillis,
                new BinanceApiCredentials("", "")
        );

        // disabled:// scheme 在构造 HttpRequest.uri() 时即 loud fail-closed，请求不会触达网络。
        // 使用 unsigned GET 以避开 credential 校验，专门验证 endpoint 层的 fail-closed。
        assertThrows(
                IllegalArgumentException.class,
                () -> client.get("/api/v3/ping", Map.of(), false, "trc-no-real-binance")
        );
    }
}
