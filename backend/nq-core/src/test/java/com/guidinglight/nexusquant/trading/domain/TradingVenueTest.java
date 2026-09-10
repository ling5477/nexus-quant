package com.guidinglight.nexusquant.trading.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class TradingVenueTest {
    @ParameterizedTest
    @ValueSource(strings = {"OKX", "okx", "Okx", "oKx", " OKX ", "\tokx\r\n"})
    void oneIdentityForExternalRepresentations(String input) {
        assertSame(TradingVenue.OKX, TradingVenue.parse(input));
    }

    @ParameterizedTest
    @CsvSource({"PAPER,PAPER", "paper,PAPER", "BINANCE,BINANCE", "binance,BINANCE"})
    void keepsExistingOrdinaryAdapters(String input, TradingVenue expected) {
        assertSame(expected, TradingVenue.parse(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "foo", "okx-test", "OKX_SPOT", "SIM", "ＯＫＸ"})
    void unknownIdentityCannotBecomeAdapterRoute(String input) {
        assertThrows(IllegalArgumentException.class, () -> TradingVenue.parse(input));
    }
}
