package com.guidinglight.nexusquant.adapter.binance.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * BinanceWsProtocolTest 覆盖私有 WS URL 组装与退避算法。
 */
class BinanceWsProtocolTest {

    @Test
    void shouldBuildUserDataStreamUrl() {
        assertEquals(
                "wss://stream.testnet.binance.vision/ws/listen-key",
                BinanceWsProtocol.buildUserDataStreamUrl("wss://stream.testnet.binance.vision/ws", "listen-key")
        );
        assertEquals(
                "wss://stream.binance.com:9443/ws/listen-key",
                BinanceWsProtocol.buildUserDataStreamUrl("wss://stream.binance.com:9443/ws/", "listen-key")
        );
    }

    @Test
    void shouldFailClosedToSentinelWhenWsUrlBlank() {
        // Why: No-real hardening (GateL-1B-A) —— blank/missing WS URL 必须 fail-closed 到 no-real sentinel，
        // 禁止回退到 testnet/mainnet ws-api host。
        assertEquals(
                "disabled://binance-ws-not-configured",
                BinanceWsProtocol.resolveUserDataWsApiUrl(null)
        );
        assertEquals(
                "disabled://binance-ws-not-configured",
                BinanceWsProtocol.resolveUserDataWsApiUrl("   ")
        );
    }

    @Test
    void shouldHonorExplicitWsApiUrlVerbatim() {
        // 显式 ws-api URL 按原样使用（仅去除尾部 '/'）；真实 endpoint 始终由显式 env opt-in。
        assertEquals(
                "wss://ws-api.binance.com:443/ws-api/v3",
                BinanceWsProtocol.resolveUserDataWsApiUrl("wss://ws-api.binance.com:443/ws-api/v3/")
        );
    }

    @Test
    void shouldNotRewriteLegacyStreamUrlToRealWsApiHost() {
        // Why: 旧实现会把 legacy stream host 静默改写成真实 ws-api host，会在 guard 关闭时构造真实网络 URI。
        // hardening 后该改写被移除：显式 legacy URL 按原样保留，不再自动指向 testnet/mainnet ws-api。
        assertEquals(
                "wss://stream.testnet.binance.vision/ws",
                BinanceWsProtocol.resolveUserDataWsApiUrl("wss://stream.testnet.binance.vision/ws")
        );
        assertEquals(
                "wss://stream.binance.com:9443/ws",
                BinanceWsProtocol.resolveUserDataWsApiUrl("wss://stream.binance.com:9443/ws")
        );
    }

    @Test
    void shouldComputeReconnectDelayWithCap() {
        assertEquals(1_000L, BinanceWsProtocol.reconnectDelayMs(1, 1_000L, 30_000L));
        assertEquals(2_000L, BinanceWsProtocol.reconnectDelayMs(2, 1_000L, 30_000L));
        assertEquals(30_000L, BinanceWsProtocol.reconnectDelayMs(10, 1_000L, 30_000L));
    }
}
