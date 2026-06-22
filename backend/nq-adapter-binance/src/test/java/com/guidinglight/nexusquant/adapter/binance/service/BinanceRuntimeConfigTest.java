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
        // baseUrl 由显式 env 提供，验证 explicit env 仍可覆盖 disabled:// 默认。
        assertEquals("https://testnet.binance.vision", config.baseUrl());
        // 该用例未提供 NQ_BINANCE_DOME_WS_URL，wsUrl 回退到默认值；No-real hardening 后默认已是 no-real sentinel。
        assertEquals("disabled://binance-ws-not-configured", config.wsUrl());
        assertEquals("abcd1234wxyz", config.credentials().apiKey());
        assertEquals(Duration.ofSeconds(-1), config.signedTimestampOffset());
        assertEquals(Duration.ofMinutes(1), config.exchangeInfoRefreshInterval());
        assertTrue(config.wsDiagnosticEnabled());
        assertEquals(1_699L, config.signedEpochMillis(2_699L));
        assertTrue(config.fingerprint().contains("signedTimestampOffsetMs=-1000"));
        assertTrue(config.fingerprint().contains("apiKey=abcd...wxyz"));
    }

    @Test
    void shouldDefaultToNonRealSentinelEndpointsWhenEnvAbsent() {
        // Why: No-real hardening (GateL-1B-A) —— 代码级默认 endpoint 必须是 no-real sentinel，
        // dome/real 均不得把 testnet/mainnet host 作为默认值。
        BinanceRuntimeConfig domeDefault = BinanceRuntimeConfig.fromEnvironment(Map.of());
        assertEquals("dome", domeDefault.envName());
        assertEquals("disabled://binance-not-configured", domeDefault.baseUrl());
        assertEquals("disabled://binance-ws-not-configured", domeDefault.wsUrl());

        BinanceRuntimeConfig realDefault = BinanceRuntimeConfig.fromEnvironment(Map.of("NQ_BINANCE_ENV", "real"));
        assertEquals("real", realDefault.envName());
        assertEquals("disabled://binance-not-configured", realDefault.baseUrl());
        assertEquals("disabled://binance-ws-not-configured", realDefault.wsUrl());

        // 默认值不得包含任何真实 Binance host。
        for (BinanceRuntimeConfig config : new BinanceRuntimeConfig[] {domeDefault, realDefault}) {
            for (String endpoint : new String[] {config.baseUrl(), config.wsUrl()}) {
                assertFalse(endpoint.contains("binance.com"), () -> "default endpoint must not contain binance.com: " + endpoint);
                assertFalse(endpoint.contains("binance.vision"), () -> "default endpoint must not contain binance.vision: " + endpoint);
            }
        }
    }

    @Test
    void shouldNotFallBackToRealEndpointWhenWsUrlBlank() {
        // Why: blank override 不得回退到 testnet/mainnet，必须 fail-closed 到 no-real sentinel。
        BinanceRuntimeConfig config = BinanceRuntimeConfig.fromEnvironment(Map.of(
                "NQ_BINANCE_ENV", "real",
                "NQ_BINANCE_REAL_WS_URL", "   "
        ));

        assertEquals("disabled://binance-ws-not-configured", config.wsUrl());
    }

    @Test
    void shouldKeepExplicitLegacyWsUrlWithoutRewritingToRealWsApiHost() {
        // Why: legacy stream URL 不再被代码静默改写成真实 ws-api host；显式配置按原样保留（仅去除尾部 '/'），
        // 真实 endpoint 始终由显式 env 决定，不得由解析逻辑自动指向 testnet/mainnet ws-api。
        BinanceRuntimeConfig config = BinanceRuntimeConfig.fromEnvironment(Map.of(
                "NQ_BINANCE_ENV", "dome",
                "NQ_BINANCE_DOME_WS_URL", "wss://stream.testnet.binance.vision/ws/",
                "NQ_BINANCE_DOME_API_KEY", "abcd1234wxyz",
                "NQ_BINANCE_DOME_API_SECRET", "secret"
        ));

        assertEquals("wss://stream.testnet.binance.vision/ws", config.wsUrl());
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
