package com.guidinglight.nexusquant.marketdata.domain.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetPage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class MarketdataDatasetListQueryTest {
    @Test
    void preservesLiteralNameUuidSourceAndZeroGapWhileNormalizingDimensions() {
        UUID id = UUID.randomUUID();
        var query =
                new MarketdataDatasetListQuery(
                        id,
                        " %_! ",
                        " binance ",
                        " spot ",
                        "bnb-usdt",
                        "1m",
                        "ready",
                        "ok",
                        "marketdata_bars",
                        0L,
                        100,
                        Integer.MAX_VALUE);
        assertEquals(id, query.datasetId());
        assertEquals("%_!", query.datasetName());
        assertEquals("BINANCE", query.exchangeCode());
        assertEquals("BNB-USDT", query.symbol());
        assertEquals("READY", query.status());
        assertEquals("OK", query.qualityStatus());
        assertEquals("marketdata_bars", query.source());
        assertEquals(0L, query.maximumGapCount());
        assertNull(query("  ", 20, 0).datasetName());
    }

    @Test
    void rejectsInvalidWindowsRawControlCharactersAndOversizeNames() {
        for (int limit : new int[] {-1, 0, 101})
            assertThrows(IllegalArgumentException.class, () -> query(null, limit, 0));
        assertThrows(IllegalArgumentException.class, () -> query(null, 20, -1));
        for (String name : new String[] {"\n", "name\t", "\u007f", "n".repeat(256)})
            assertThrows(IllegalArgumentException.class, () -> query(name, 20, 0));
        query("n".repeat(255), 1, 0);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MarketdataDatasetListQuery(
                                null, null, null, null, null, null, null, null, null, -1L, 20, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MarketdataDatasetListQuery(
                                null,
                                null,
                                "x".repeat(65),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                20,
                                0));
    }

    @Test
    void rejectsTruncatedPagesAndAcceptsAnOffsetPastTheLastRow() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketdataDatasetPage(List.of(), 1, 20, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MarketdataDatasetPage(List.of(), -1, 20, 0));
        assertEquals(0, new MarketdataDatasetPage(List.of(), 3, 20, 100).items().size());
    }

    private static MarketdataDatasetListQuery query(String name, int limit, int offset) {
        return new MarketdataDatasetListQuery(
                null, name, null, null, null, null, null, null, null, null, limit, offset);
    }
}
