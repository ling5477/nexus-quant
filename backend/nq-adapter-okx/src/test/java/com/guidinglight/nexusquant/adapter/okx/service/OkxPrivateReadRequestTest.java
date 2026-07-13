package com.guidinglight.nexusquant.adapter.okx.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OkxPrivateReadRequestTest {

    @Test
    void exposesExactlyTwoFixedPrivateReadOperations() {
        assertEquals(2, OkxPrivateReadOperation.values().length);
        assertEquals("GET", OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ.method());
        assertEquals("/api/v5/account/config", OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ.path());
        assertEquals("GET", OkxPrivateReadOperation.OKX_ACCOUNT_BALANCE_READ.method());
        assertEquals("/api/v5/account/balance", OkxPrivateReadOperation.OKX_ACCOUNT_BALANCE_READ.path());
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
