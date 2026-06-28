package com.guidinglight.nexusquant.adapter.okx.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OkxRuntimeConfigTest 覆盖 GateC-1 模拟盘的环境选择与脱敏指纹。
 */
class OkxRuntimeConfigTest {

    @Test
    void shouldIgnoreEnvCredentialsAndKeepExplicitEndpoints() {
        // Why: No-real hardening (GateL-1B-B) —— runtime config 不再读取进程环境 credential；
        // 非敏感 endpoint 等 transport metadata 仍可由显式 env 覆盖。
        OkxRuntimeConfig config = OkxRuntimeConfig.fromEnvironment(Map.of(
                "NQ_OKX_ENV", "dome",
                "NQ_OKX_DOME_BASE_URL", "https://www.okx.com",
                "NQ_OKX_DOME_API_KEY", "env-marker-key-ignored",
                "NQ_OKX_DOME_API_SECRET", "env-marker-secret-ignored",
                "NQ_OKX_DOME_API_PASSPHRASE", "env-marker-pass-ignored",
                "NQ_OKX_TIMEOUT_MS", "3000"
        ));

        assertEquals("dome", config.envName());
        // 非敏感 endpoint 显式 env 仍生效。
        assertEquals("https://www.okx.com", config.baseUrl());
        // 该用例未提供 NQ_OKX_WS_URL，wsPrivateUrl 回退到 no-real sentinel。
        assertEquals("disabled://okx-ws-not-configured", config.wsPrivateUrl());
        assertTrue(config.simulatedTrading());
        // credential 被忽略：unconfigured，不持有 env 值，fingerprint 不回显任何 env credential marker。
        assertFalse(config.credentials().isConfigured());
        assertEquals("", config.credentials().apiKey());
        assertEquals("", config.credentials().secretKey());
        assertEquals("", config.credentials().passphrase());
        assertTrue(config.fingerprint().contains("credentialKeyFingerprint=missing"));
        assertFalse(config.fingerprint().contains("env-marker"));
    }

    @Test
    void shouldDefaultToNonRealSentinelEndpointsWhenEnvAbsent() {
        // Why: 代码级默认 endpoint 必须是 no-real sentinel，禁止真实交易所 host 作为默认值。
        OkxRuntimeConfig domeDefault = OkxRuntimeConfig.fromEnvironment(Map.of());
        assertEquals("dome", domeDefault.envName());
        assertEquals("disabled://okx-not-configured", domeDefault.baseUrl());
        assertEquals("disabled://okx-ws-not-configured", domeDefault.wsPrivateUrl());
        assertTrue(domeDefault.simulatedTrading());

        OkxRuntimeConfig realDefault = OkxRuntimeConfig.fromEnvironment(Map.of("NQ_OKX_ENV", "real"));
        assertEquals("real", realDefault.envName());
        assertEquals("disabled://okx-not-configured", realDefault.baseUrl());
        assertEquals("disabled://okx-ws-not-configured", realDefault.wsPrivateUrl());
        assertFalse(realDefault.simulatedTrading());

        // 默认值不得包含任何真实 OKX host。
        for (OkxRuntimeConfig config : new OkxRuntimeConfig[] {domeDefault, realDefault}) {
            for (String endpoint : new String[] {config.baseUrl(), config.wsPrivateUrl()}) {
                assertFalse(endpoint.contains("okx.com"), () -> "default endpoint must not contain okx.com: " + endpoint);
                assertFalse(endpoint.contains("ws.okx.com"), () -> "default endpoint must not contain ws.okx.com: " + endpoint);
                assertFalse(endpoint.contains("wspap.okx.com"), () -> "default endpoint must not contain wspap.okx.com: " + endpoint);
            }
        }
    }

    @Test
    void shouldTreatLegacyDemoAsDome() {
        OkxRuntimeConfig config = OkxRuntimeConfig.fromEnvironment(Map.of(
                "NQ_OKX_ENV", "demo",
                "NQ_OKX_DOME_API_KEY", "legacy-demo-key"
        ));

        assertEquals("dome", config.envName());
        assertTrue(config.simulatedTrading());
    }

    @Test
    void shouldPreferUnifiedRuntimeEndpointVariablesAndIgnoreCredentials() {
        // Why: 统一 transport 变量（baseUrl/wsUrl）仍优先生效；credential 一律被忽略并保持 unconfigured。
        OkxRuntimeConfig config = OkxRuntimeConfig.fromEnvironment(Map.of(
                "NQ_OKX_ENV", "real",
                "NQ_OKX_API_KEY", "real-unified-key-ignored",
                "NQ_OKX_API_SECRET", "real-unified-secret-ignored",
                "NQ_OKX_API_PASSPHRASE", "real-unified-pass-ignored",
                "NQ_OKX_BASE_URL", "https://real.example.com",
                "NQ_OKX_WS_URL", "wss://real.example.com/ws/private",
                "NQ_OKX_REAL_API_KEY", "legacy-real-key-ignored"
        ));

        assertEquals("real", config.envName());
        assertEquals("https://real.example.com", config.baseUrl());
        assertEquals("wss://real.example.com/ws/private", config.wsPrivateUrl());
        assertFalse(config.simulatedTrading());
        // credential 被忽略：unconfigured。
        assertFalse(config.credentials().isConfigured());
        assertEquals("", config.credentials().apiKey());
        assertFalse(config.fingerprint().contains("real-unified"));
    }
}
