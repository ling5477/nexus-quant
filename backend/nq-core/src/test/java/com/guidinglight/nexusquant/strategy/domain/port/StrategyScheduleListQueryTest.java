package com.guidinglight.nexusquant.strategy.domain.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.Locale;

/** 拒绝无策略范围和越界窗口，不让非法值退化成无条件目录读取。 */
class StrategyScheduleListQueryTest {
    @Test
    void normalizesExactIdentityAndStatus() {
        var query =
                new StrategyScheduleListQuery(
                        " str ", "sch-%_!", "disabled", 100, Integer.MAX_VALUE);
        assertEquals("str", query.strategyId());
        assertEquals("sch-%_!", query.scheduleId());
        assertEquals("DISABLED", query.status());
    }

    @Test
    void legacyTypesAreReadFiltersOnly() {
        for (String type : new String[] {"cron", "interval", "manual"})
            assertEquals(
                    type.toUpperCase(Locale.ROOT),
                    new StrategyScheduleListQuery("str", null, null, type, 20, 0).scheduleType());
        assertEquals(null, new StrategyScheduleListQuery("str", null, null, 20, 0).scheduleType());
        for (String type : new String[] {"RUNNING", "LIVE", "manual\n"})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new StrategyScheduleListQuery("str", null, null, type, 20, 0));
    }

    @Test
    void rejectsInvalidScopeFiltersAndWindows() {
        for (String id : new String[] {null, " ", "str\t", "x".repeat(129)})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new StrategyScheduleListQuery(id, null, null, 20, 0));
        for (String status : new String[] {"RUNNING", "LIVE", "enabled\n"})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new StrategyScheduleListQuery("str", null, status, 20, 0));
        for (int[] window : new int[][] {{0, 0}, {101, 0}, {20, -1}})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new StrategyScheduleListQuery("str", null, null, window[0], window[1]));
        assertThrows(
                IllegalArgumentException.class,
                () -> new StrategyScheduleListQuery("str", "x".repeat(129), null, 20, 0));
    }
}
