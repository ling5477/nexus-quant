package com.guidinglight.nexusquant.adapter.okx.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OkxPrivateReadRequestTest {

    @Test
    void exposesOnlyFiveFixedPrivateReadOperations() {
        assertEquals(5, OkxPrivateReadOperation.values().length);
        assertEquals("GET", OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ.method());
        assertEquals("/api/v5/account/config", OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ.path());
        assertEquals("GET", OkxPrivateReadOperation.OKX_ACCOUNT_BALANCE_READ.method());
        assertEquals("/api/v5/account/balance", OkxPrivateReadOperation.OKX_ACCOUNT_BALANCE_READ.path());
        assertEquals("/api/v5/trade/orders-pending", OkxPrivateReadOperation.OKX_SPOT_OPEN_ORDERS_READ.path());
        assertEquals("/api/v5/trade/orders-history", OkxPrivateReadOperation.OKX_SPOT_ORDER_HISTORY_READ.path());
        assertEquals("/api/v5/trade/fills", OkxPrivateReadOperation.OKX_SPOT_RECENT_FILLS_READ.path());
    }

    @Test
    void buildsBoundedSpotReconciliationQueriesWithoutArbitraryPathOrQueryMap() {
        Instant begin = Instant.parse("2026-07-14T00:00:00Z");
        Instant end = Instant.parse("2026-07-14T01:00:00Z");

        assertEquals(
                "/api/v5/trade/orders-pending?instType=SPOT&instId=BTC-USDT&limit=100",
                OkxPrivateReadRequest.openOrders("btc-usdt", 100).pathWithQuery()
        );
        assertEquals(
                "/api/v5/trade/orders-history?instType=SPOT&instId=BTC-USDT&begin=1783987200000&end=1783990800000&limit=50",
                OkxPrivateReadRequest.orderHistory("BTC-USDT", begin, end, 50).pathWithQuery()
        );
        assertEquals(
                "/api/v5/trade/fills?instType=SPOT&instId=BTC-USDT&begin=1783987200000&end=1783990800000&limit=50",
                OkxPrivateReadRequest.recentFills("BTC-USDT", begin, end, 50).pathWithQuery()
        );
    }

    @Test
    void rejectsUnknownSymbolsUnboundedWindowsAndRecordLimits() {
        Instant begin = Instant.parse("2026-07-14T00:00:00Z");
        Instant end = begin.plusSeconds(24 * 3600L + 1);
        for (String invalid : List.of("BTC/USDT", "BTC-USDT&ordId=1", "https://okx.com", "BTC-USDT-SWAP")) {
            assertThrows(IllegalArgumentException.class, () -> OkxPrivateReadRequest.openOrders(invalid, 100));
        }
        assertThrows(IllegalArgumentException.class, () -> OkxPrivateReadRequest.openOrders("BTC-USDT", 101));
        assertThrows(IllegalArgumentException.class,
                () -> OkxPrivateReadRequest.orderHistory("BTC-USDT", begin, end, 100));
        assertThrows(IllegalArgumentException.class, () -> new OkxPrivateReadRequest(
                OkxPrivateReadOperation.OKX_SPOT_OPEN_ORDERS_READ, List.of()
        ));
    }

    @Test
    void canonicalizesBalanceCurrenciesWithoutArbitraryQueryMap() {
        OkxPrivateReadRequest request = OkxPrivateReadRequest.accountBalance(List.of("eth", "BTC", "eth"));

        assertEquals(List.of("BTC", "ETH"), request.currencies());
        assertEquals("/api/v5/account/balance?ccy=BTC,ETH", request.pathWithQuery());
    }

    @Test
    void rejectsInvalidOrOversizedCurrencyAllowlistAndConfigQuery() {
        assertThrows(IllegalArgumentException.class,
                () -> OkxPrivateReadRequest.accountBalance(null));
        assertThrows(IllegalArgumentException.class,
                () -> OkxPrivateReadRequest.accountBalance(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> OkxPrivateReadRequest.accountBalance(List.of("BTC", "ETH", "USDT", "OKB")));
        for (String invalidCurrency : List.of(
                "BTC/../../trade", "BTC,ETH", "BT C", "BTC%20", "比特币", "BTC&x=1", "BTC=ETH"
        )) {
            assertThrows(IllegalArgumentException.class,
                    () -> OkxPrivateReadRequest.accountBalance(List.of(invalidCurrency)));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new OkxPrivateReadRequest(
                        OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ,
                        List.of("BTC")
                ));
    }
}
