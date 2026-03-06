package com.guidinglight.nexusquant.adapter.binance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * BinanceRuntimeConfigTest 覆盖 Binance 环境选择、刷新窗口与脱敏指纹。
 */
class BinanceRuntimeConfigTest {

    @Test
    void shouldSelectDomeCredentialsAndMaskApiKey() {
        BinanceRuntimeConfig config = BinanceRuntimeConfig.fromEnvironment(Map.of(
                "NQ_BINANCE_ENV", "dome",
                "NQ_BINANCE_DOME_BASE_URL", "https://testnet.binance.vision",
                "NQ_BINANCE_DOME_API_KEY", "abcd1234wxyz",
                "NQ_BINANCE_DOME_API_SECRET", "secret",
                "NQ_BINANCE_TIMEOUT_MS", "3000",
                "NQ_BINANCE_EXCHANGE_INFO_REFRESH_MS", "60000"
        ));

        assertEquals("dome", config.envName());
        assertEquals("https://testnet.binance.vision", config.baseUrl());
        assertEquals("abcd1234wxyz", config.credentials().apiKey());
        assertEquals(Duration.ofMinutes(1), config.exchangeInfoRefreshInterval());
        assertTrue(config.fingerprint().contains("apiKey=abcd...wxyz"));
    }

    @Test
    void shouldTreatLegacyDemoAsDome() {
        BinanceRuntimeConfig config = BinanceRuntimeConfig.fromEnvironment(Map.of(
                "NQ_BINANCE_ENV", "demo",
                "NQ_BINANCE_DOME_API_KEY", "legacy-demo-key"
        ));

        assertEquals("dome", config.envName());
    }
}
