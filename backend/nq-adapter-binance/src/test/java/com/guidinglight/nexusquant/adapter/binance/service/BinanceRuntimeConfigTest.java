package com.guidinglight.nexusquant.adapter.binance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.adapter.binance.model.BinanceKeyType;

import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void shouldSelectEd25519CredentialsFromPrivateKeyPath() throws Exception {
        Path privateKeyFile = Files.createTempFile("binance-ed25519", ".pem");
        try {
            Files.writeString(privateKeyFile, "-----BEGIN PRIVATE KEY-----\nZm9v\n-----END PRIVATE KEY-----");
            BinanceRuntimeConfig config = BinanceRuntimeConfig.fromEnvironment(Map.of(
                    "NQ_BINANCE_ENV", "real",
                    "NQ_BINANCE_KEY_TYPE", "ed25519",
                    "NQ_BINANCE_REAL_BASE_URL", "https://api.binance.com",
                    "NQ_BINANCE_REAL_API_KEY", "real-api-key",
                    "NQ_BINANCE_REAL_PRIVATE_KEY_PATH", privateKeyFile.toString()
            ));

            assertEquals("real", config.envName());
            assertEquals(BinanceKeyType.ED25519, config.credentials().keyType());
            assertEquals(privateKeyFile.toString(), config.credentials().privateKeyPath());
            assertTrue(config.credentials().isConfigured());
        } finally {
            Files.deleteIfExists(privateKeyFile);
        }
    }

    @Test
    void shouldMarkEd25519ConfigAsIncompleteWhenPrivateKeyMissing() {
        BinanceRuntimeConfig config = BinanceRuntimeConfig.fromEnvironment(Map.of(
                "NQ_BINANCE_ENV", "real",
                "NQ_BINANCE_KEY_TYPE", "ed25519",
                "NQ_BINANCE_REAL_API_KEY", "real-api-key"
        ));

        assertEquals(BinanceKeyType.ED25519, config.credentials().keyType());
        assertFalse(config.credentials().isConfigured());
    }
}
