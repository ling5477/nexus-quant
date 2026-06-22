package com.guidinglight.nexusquant.adapter.binance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * BinanceNoRealCredentialHardeningTest 固定 GateL-1B-B 的 no-real credential source 边界。
 * <p>
 * Why:
 * GateL-1 review 把“Binance runtime config 直接解析进程 credential”记为 P1-B。
 * 本测试证明：默认 runtime config 不持有 credential；private(signed) 请求在 credential 未配置时
 * 于网络前 loud fail-closed（BINANCE_CREDENTIALS_MISSING），且失败信息不含 credential material。
 * 本测试只在本地执行，不访问任何外部服务。
 */
class BinanceNoRealCredentialHardeningTest {

    @Test
    void shouldDefaultRuntimeConfigToUnconfiguredCredential() {
        BinanceRuntimeConfig config = BinanceRuntimeConfig.fromEnvironment(Map.of());

        assertFalse(config.credentials().isConfigured());
        assertEquals("", config.credentials().apiKey());
        assertEquals("", config.credentials().secretKey());
    }

    @Test
    void shouldFailClosedBeforeNetworkOnSignedRequestWhenCredentialUnconfigured() {
        // 使用非 sentinel 的本地 baseUrl，确保 fail-closed 来自 credential 层（ensureCredentials），
        // 而非 P1-A 的 disabled:// endpoint sentinel；signed 请求在构造 URI / 发送前即抛错。
        BinanceHttpClient client = new BinanceHttpClient(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                "http://127.0.0.1:9",
                Duration.ofSeconds(1),
                new BinanceRequestSigner(),
                System::currentTimeMillis,
                BinanceApiCredentials.unconfigured()
        );

        BinanceApiException ex = assertThrows(
                BinanceApiException.class,
                () -> client.get("/api/v3/account", Map.of(), true, "trc-no-real-cred-binance")
        );

        assertEquals("BINANCE_CREDENTIALS_MISSING", ex.errorCode());
        // 失败信息不含 credential material。
        assertFalse(ex.getMessage().toLowerCase().contains("secret"));
        assertFalse(ex.getMessage().contains("apiKey="));
    }
}
