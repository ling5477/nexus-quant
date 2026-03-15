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
 * BinanceRuntimeConfigTest 覆盖 Binance 环境切换、时间偏移与脱敏指纹。
 */
class BinanceRuntimeConfigTest {

    @Test
    void shouldSelectDomeCredentialsAndMaskApiKey() {
        BinanceRuntimeConfig config = BinanceRuntimeConfig.fromEnvironment(Map.of(
                "NQ_BINANCE_ENV", "dome",
                "NQ_BINANCE_DOME_BASE_URL", "https://testnet.binance.vision",
                "NQ_BINANCE_DOME_API_KEY", "abcd1234wxyz",
                "NQ_BINANCE_DOME_API_SECRET", "secret",
                "NQ_BINANCE_WS_DIAGNOSTIC_ENABLED", "true",
                "NQ_BINANCE_TIMEOUT_MS", "3000",
                "NQ_BINANCE_SIGNED_TIMESTAMP_OFFSET_MS", "-1000",
                "NQ_BINANCE_EXCHANGE_INFO_REFRESH_MS", "60000"
        ));

        assertEquals("dome", config.envName());
        assertEquals("https://testnet.binance.vision", config.baseUrl());
        assertEquals("wss://ws-api.testnet.binance.vision/ws-api/v3", config.wsUrl());
        assertEquals("abcd1234wxyz", config.credentials().apiKey());
        assertEquals(Duration.ofSeconds(-1), config.signedTimestampOffset());
        assertEquals(Duration.ofMinutes(1), config.exchangeInfoRefreshInterval());
        assertTrue(config.wsDiagnosticEnabled());
        assertEquals(1_699L, config.signedEpochMillis(2_699L));
        assertTrue(config.fingerprint().contains("signedTimestampOffsetMs=-1000"));
        assertTrue(config.fingerprint().contains("apiKey=abcd...wxyz"));
    }

    @Test
    void shouldNormalizeLegacyDomeStreamUrlToOfficialWsApiUrl() {
        BinanceRuntimeConfig config = BinanceRuntimeConfig.fromEnvironment(Map.of(
                "NQ_BINANCE_ENV", "dome",
                "NQ_BINANCE_DOME_WS_URL", "wss://stream.testnet.binance.vision/ws",
                "NQ_BINANCE_DOME_API_KEY", "abcd1234wxyz",
                "NQ_BINANCE_DOME_API_SECRET", "secret"
        ));

        assertEquals("wss://ws-api.testnet.binance.vision/ws-api/v3", config.wsUrl());
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
                    "NQ_BINANCE_REAL_WS_URL", "wss://ws-api.binance.com:443/ws-api/v3",
                    "NQ_BINANCE_REAL_API_KEY", "real-api-key",
                    "NQ_BINANCE_REAL_PRIVATE_KEY_PATH", privateKeyFile.toString(),
                    "NQ_BINANCE_WS_RECONNECT_BASE_DELAY_MS", "2000",
                    "NQ_BINANCE_WS_RECONNECT_MAX_DELAY_MS", "5000",
                    "NQ_BINANCE_WS_HEARTBEAT_INTERVAL_MS", "15000",
                    "NQ_BINANCE_LISTENKEY_REFRESH_MS", "1200000"
            ));

            assertEquals("real", config.envName());
            assertEquals("wss://ws-api.binance.com:443/ws-api/v3", config.wsUrl());
            assertEquals(BinanceKeyType.ED25519, config.credentials().keyType());
            assertEquals(privateKeyFile.toString(), config.credentials().privateKeyPath());
            assertEquals(Duration.ofSeconds(2), config.wsReconnectBase());
            assertEquals(Duration.ofSeconds(5), config.wsReconnectMax());
            assertEquals(Duration.ofSeconds(15), config.wsHeartbeat());
            assertEquals(Duration.ofMinutes(20), config.listenKeyRefreshInterval());
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
