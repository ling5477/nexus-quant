package com.guidinglight.nexusquant.strategy.domain.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StrategyDefinitionListQueryTest {
    @Test
    void keepsLiteralFiltersAndFalseWhileNormalizingBlankText() {
        var query =
                new StrategyDefinitionListQuery(
                        " id ", " %_! ", " ", null, "okx", "sim", false, 100, Integer.MAX_VALUE);
        assertEquals("id", query.strategyId());
        assertEquals("%_!", query.strategyCode());
        assertNull(query.strategyName());
        assertFalse(query.enabled());
        assertEquals(Integer.MAX_VALUE, query.offset());
    }

    @Test
    void rejectsInvalidWindowAndRawControlCharacters() {
        for (int limit : new int[] {0, 101, -1}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new StrategyDefinitionListQuery(
                                    null, null, null, null, null, null, null, limit, 0));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StrategyDefinitionListQuery(
                                null, null, null, null, null, null, null, 20, -1));
        for (String text : new String[] {"\n", "id\t", "\u007f"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new StrategyDefinitionListQuery(
                                    null, text, null, null, null, null, null, 20, 0));
        }
    }

    @Test
    void enforcesFieldLengthsWithoutTruncating() {
        new StrategyDefinitionListQuery(
                "i".repeat(128),
                "c".repeat(128),
                "n".repeat(255),
                "t".repeat(64),
                "e".repeat(64),
                "s".repeat(64),
                null,
                1,
                0);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StrategyDefinitionListQuery(
                                "i".repeat(129), null, null, null, null, null, null, 20, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StrategyDefinitionListQuery(
                                null, null, "n".repeat(256), null, null, null, null, 20, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new StrategyDefinitionListQuery(
                                null, null, null, "t".repeat(65), null, null, null, 20, 0));
    }
}
