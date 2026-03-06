package com.guidinglight.nexusquant.adapter.okx.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OkxRuntimeConfigTest 覆盖 GateC-1 模拟盘的环境选择与脱敏指纹。
 */
class OkxRuntimeConfigTest {

    @Test
    void shouldSelectDomeCredentialsAndMaskApiKey() {
        OkxRuntimeConfig config = OkxRuntimeConfig.fromEnvironment(Map.of(
                "NQ_OKX_ENV", "dome",
                "NQ_OKX_DOME_BASE_URL", "https://www.okx.com",
                "NQ_OKX_DOME_API_KEY", "abcd1234wxyz",
                "NQ_OKX_DOME_API_SECRET", "secret",
                "NQ_OKX_DOME_API_PASSPHRASE", "pass",
                "NQ_OKX_TIMEOUT_MS", "3000"
        ));

        assertEquals("dome", config.envName());
        assertEquals("https://www.okx.com", config.baseUrl());
        assertEquals("wss://wspap.okx.com:8443/ws/v5/private", config.wsPrivateUrl());
        assertEquals("abcd1234wxyz", config.credentials().apiKey());
        assertTrue(config.simulatedTrading());
        assertTrue(config.fingerprint().contains("apiKey=abcd...wxyz"));
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
}
