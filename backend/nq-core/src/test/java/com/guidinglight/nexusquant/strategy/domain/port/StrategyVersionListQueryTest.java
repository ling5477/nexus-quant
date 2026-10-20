package com.guidinglight.nexusquant.strategy.domain.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.strategy.domain.StrategyVersionPage;

import org.junit.jupiter.api.Test;

import java.util.List;

class StrategyVersionListQueryTest {
    @Test
    void keepsLiteralNameAndExactParentWhileNormalizingStatus() {
        var query =
                new StrategyVersionListQuery(
                        " parent ", " id ", " %_! ", 1, " archived ", 100, Integer.MAX_VALUE);
        assertEquals("parent", query.strategyCode());
        assertEquals("id", query.strategyVersionId());
        assertEquals("%_!", query.versionName());
        assertEquals("ARCHIVED", query.status());
        assertEquals(Integer.MAX_VALUE, query.offset());
        var blanks = new StrategyVersionListQuery("p", " ", " ", null, " ", 20, 0);
        assertNull(blanks.strategyVersionId());
        assertNull(blanks.versionName());
        assertNull(blanks.status());
    }

    @Test
    void rejectsMissingParentInvalidStateVersionAndWindows() {
        for (String parent : new String[] {null, "", " ", "p\t", "x".repeat(129)})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new StrategyVersionListQuery(parent, null, null, null, null, 20, 0));
        for (int version : new int[] {0, -1})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new StrategyVersionListQuery("p", null, null, version, null, 20, 0));
        for (int limit : new int[] {0, -1, 101})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new StrategyVersionListQuery("p", null, null, null, null, limit, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StrategyVersionListQuery("p", null, null, null, null, 20, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StrategyVersionListQuery("p", null, null, null, "RUNNING", 20, 0));
    }

    @Test
    void rejectsRawControlCharactersAndOverlongFieldsWithoutTruncation() {
        new StrategyVersionListQuery(
                "p".repeat(128),
                "i".repeat(128),
                "n".repeat(128),
                Integer.MAX_VALUE,
                "DRAFT",
                1,
                0);
        for (String invalid : new String[] {"\n", "a\t", "\u007f", "x".repeat(129)}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new StrategyVersionListQuery("p", invalid, null, null, null, 20, 0));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new StrategyVersionListQuery("p", null, invalid, null, null, 20, 0));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new StrategyVersionListQuery("p", null, null, null, "DRAFT\t", 20, 0));
    }

    @Test
    void acceptsEmptyTailButRejectsTruncatedOrInvalidPage() {
        assertEquals(100L, new StrategyVersionPage(List.of(), 100, 20, 100).total());
        assertThrows(
                IllegalArgumentException.class, () -> new StrategyVersionPage(List.of(), 1, 20, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StrategyVersionPage(List.of(), -1, 20, 0));
    }
}
