package com.guidinglight.nexusquant.strategy.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.application.command.StrategyScheduleCreateRequest;
import com.guidinglight.nexusquant.strategy.domain.StrategyScheduleCreationSpec;

import org.junit.jupiter.api.Test;

import java.util.List;

/** 覆盖固定输入、完整指纹和时区/每日窗口语义，不把每日跨午夜误当绝对日期逆序。 */
class StrategyScheduleCreationInputsTest {
    private final StrategyScheduleCreationInputs inputs =
            new StrategyScheduleCreationInputs(new ObjectMapper());

    private StrategyScheduleCreationSpec spec(String window) {
        return inputs.normalize(
                new StrategyScheduleCreateRequest(
                        " str ", "cron", " 0 * * * * * ", "UTC", false, window, "schedule_window"));
    }

    @Test
    void preservesNativeJsonAndNormalizesOnlyStableFields() {
        String raw = "{\"value\":9007199254740993.123456789012345678}";
        var value = spec(" " + raw + " ");
        assertEquals(raw, value.windowConfig());
        assertEquals("str", value.strategyId());
        assertEquals("CRON", value.scheduleType());
        assertEquals("SCHEDULE_WINDOW", value.dedupScope());
        assertEquals(value.fingerprint(1), spec(raw).fingerprint(1));
        assertEquals(64, value.fingerprint(1).length());
    }

    @Test
    void bindsEveryInputAndOwnerWithoutDelimiterCollisions() {
        var original = spec("{}");
        for (var changed :
                List.of(
                        new StrategyScheduleCreationSpec(
                                "other",
                                original.scheduleType(),
                                original.cronExpr(),
                                original.timezone(),
                                false,
                                "{}",
                                original.dedupScope()),
                        new StrategyScheduleCreationSpec(
                                original.strategyId(),
                                original.scheduleType(),
                                "0 */5 * * * *",
                                original.timezone(),
                                false,
                                "{}",
                                original.dedupScope()),
                        new StrategyScheduleCreationSpec(
                                original.strategyId(),
                                original.scheduleType(),
                                original.cronExpr(),
                                "Asia/Shanghai",
                                false,
                                "{}",
                                original.dedupScope()),
                        new StrategyScheduleCreationSpec(
                                original.strategyId(),
                                original.scheduleType(),
                                original.cronExpr(),
                                original.timezone(),
                                true,
                                "{}",
                                original.dedupScope()),
                        new StrategyScheduleCreationSpec(
                                original.strategyId(),
                                original.scheduleType(),
                                original.cronExpr(),
                                original.timezone(),
                                false,
                                "{ }",
                                original.dedupScope()),
                        new StrategyScheduleCreationSpec(
                                original.strategyId(),
                                original.scheduleType(),
                                original.cronExpr(),
                                original.timezone(),
                                false,
                                "{}",
                                "REQUEST")))
            assertNotEquals(original.fingerprint(1), changed.fingerprint(1));
        assertNotEquals(original.fingerprint(1), original.fingerprint(2));
        assertThrows(IllegalArgumentException.class, () -> original.fingerprint(0));
    }

    @Test
    void supportsOvernightAndAllDayWindowsWithSecondPrecision() {
        for (String window :
                List.of(
                        "{\"startTime\":\"23:15:04\",\"endTime\":\"02:30:07\",\"daysOfWeek\":[\"MONDAY\"]}",
                        "{\"startTime\":\"00:00:00\",\"endTime\":\"00:00:00\"}",
                        "{\"enabled\":false}")) assertEquals(window, spec(window).windowConfig());
    }

    @Test
    void rejectsInvalidWindowsAndUtf8ByteOverflow() {
        for (String window :
                List.of(
                        "[]",
                        "null",
                        "{",
                        "{\"enabled\":\"true\"}",
                        "{\"startTime\":\"12:00:00\"}",
                        "{\"startTime\":\"25:00:00\",\"endTime\":\"12:00:00\"}",
                        "{\"timezone\":\"Mars/Unknown\"}",
                        "{\"daysOfWeek\":[\"NO_DAY\"]}",
                        "{\"label\":\"" + "汉".repeat(3000) + "\"}"))
            assertThrows(IllegalArgumentException.class, () -> spec(window));
    }

    @Test
    void rejectsBadCronTimezonesAndUnboundedIdentity() {
        for (var request :
                List.of(
                        new StrategyScheduleCreateRequest(
                                "str", "INTERVAL", "0 * * * * *", "UTC", false, "{}", "REQUEST"),
                        new StrategyScheduleCreateRequest(
                                "str", "CRON", "invalid", "UTC", false, "{}", "REQUEST"),
                        new StrategyScheduleCreateRequest(
                                "str",
                                "CRON",
                                "0 * * * * *",
                                "Mars/Unknown",
                                false,
                                "{}",
                                "REQUEST"),
                        new StrategyScheduleCreateRequest(
                                "x".repeat(129),
                                "CRON",
                                "0 * * * * *",
                                "UTC",
                                false,
                                "{}",
                                "REQUEST"),
                        new StrategyScheduleCreateRequest(
                                "str\t", "CRON", "0 * * * * *", "UTC", false, "{}", "REQUEST")))
            assertThrows(IllegalArgumentException.class, () -> inputs.normalize(request));
    }
}
