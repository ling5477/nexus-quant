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
    void shouldResolveWsApiUrlFromLegacyStreamUrl() {
        assertEquals(
                "wss://ws-api.testnet.binance.vision/ws-api/v3",
                BinanceWsProtocol.resolveUserDataWsApiUrl("wss://stream.testnet.binance.vision/ws", "dome")
        );
        assertEquals(
                "wss://ws-api.binance.com:443/ws-api/v3",
                BinanceWsProtocol.resolveUserDataWsApiUrl("wss://stream.binance.com:9443/ws", "real")
        );
        assertEquals(
                "wss://ws-api.binance.com:443/ws-api/v3",
                BinanceWsProtocol.resolveUserDataWsApiUrl("wss://ws-api.binance.com:443/ws-api/v3", "real")
        );
    }

    @Test
    void shouldComputeReconnectDelayWithCap() {
        assertEquals(1_000L, BinanceWsProtocol.reconnectDelayMs(1, 1_000L, 30_000L));
        assertEquals(2_000L, BinanceWsProtocol.reconnectDelayMs(2, 1_000L, 30_000L));
        assertEquals(30_000L, BinanceWsProtocol.reconnectDelayMs(10, 1_000L, 30_000L));
    }
}
