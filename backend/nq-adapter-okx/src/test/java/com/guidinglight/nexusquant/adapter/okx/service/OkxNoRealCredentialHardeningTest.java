package com.guidinglight.nexusquant.adapter.okx.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.model.OkxApiCredentials;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * OkxNoRealCredentialHardeningTest 固定 GateL-1B-B 的 no-real credential source 边界。
 * <p>
 * Why:
 * GateL-1 review 把“OKX runtime config 直接解析进程 credential”记为 P1-B。
 * 本测试证明：默认 runtime config 不持有 credential；authenticated 请求在 credential 未配置时
 * 于网络前 loud fail-closed（OKX_CREDENTIALS_MISSING），且失败信息不含 credential material。
 * 本测试只在本地执行，不访问任何外部服务。
 */
class OkxNoRealCredentialHardeningTest {

    @Test
    void shouldDefaultRuntimeConfigToUnconfiguredCredential() {
        OkxRuntimeConfig config = OkxRuntimeConfig.fromEnvironment(Map.of());

        assertFalse(config.credentials().isConfigured());
        assertEquals("", config.credentials().apiKey());
        assertEquals("", config.credentials().secretKey());
        assertEquals("", config.credentials().passphrase());
    }

    @Test
    void shouldFailClosedBeforeNetworkOnAuthenticatedRequestWhenCredentialUnconfigured() {
        // 使用非 sentinel 的本地 baseUrl，确保 fail-closed 来自 credential 层而非 P1-A 的 disabled:// sentinel；
        // authenticated 请求在发送前即抛 OKX_CREDENTIALS_MISSING。
        OkxHttpClient client = new OkxHttpClient(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                "http://127.0.0.1:9",
                Duration.ofSeconds(1),
                new OkxRequestSigner(),
                () -> "0",
                OkxApiCredentials.unconfigured(),
                true
        );

        OkxApiException ex = assertThrows(
                OkxApiException.class,
                () -> client.get("/api/v5/account/balance", "trc-no-real-cred-okx")
        );

        assertEquals("OKX_CREDENTIALS_MISSING", ex.errorCode());
        // 失败信息不含 credential material。
        assertFalse(ex.getMessage().toLowerCase().contains("secret"));
        assertFalse(ex.getMessage().toLowerCase().contains("passphrase"));
    }
}
