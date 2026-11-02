package com.guidinglight.nexusquant.strategy.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.application.command.StrategyScheduleCreateRequest;
import com.guidinglight.nexusquant.strategy.domain.StrategyScheduleCreationSpec;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

/** 新恢复合同先校验时间和窗口，保留原始JSON文本精度；不改变旧接口或扫描器解释规则。 */
@Component
public class StrategyScheduleCreationInputs {
    private final ObjectMapper mapper;

    public StrategyScheduleCreationInputs(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public StrategyScheduleCreationSpec normalize(StrategyScheduleCreateRequest raw) {
        if (raw == null) throw new IllegalArgumentException("schedule request required");
        String strategy = text(raw.strategyId(), 128),
                type = text(raw.scheduleType(), 32).toUpperCase(Locale.ROOT),
                cron = text(raw.cronExpr(), 128),
                zone = text(raw.timezone(), 64),
                dedup = text(raw.dedupScope(), 32).toUpperCase(Locale.ROOT);
        if (!"CRON".equals(type)) throw new IllegalArgumentException("scheduleType must be CRON");
        CronExpression.parse(cron);
        try {
            ZoneId.of(zone);
        } catch (DateTimeException invalid) {
            throw new IllegalArgumentException("invalid schedule timezone", invalid);
        }
        if (!Set.of("SCHEDULE_WINDOW", "REQUEST", "STRATEGY").contains(dedup))
            throw new IllegalArgumentException("invalid dedupScope");
        String window =
                raw.windowConfig() == null || raw.windowConfig().isBlank()
                        ? "{}"
                        : raw.windowConfig().trim();
        if (window.getBytes(StandardCharsets.UTF_8).length > 8192)
            throw new IllegalArgumentException("schedule window exceeds byte limit");
        try {
            JsonNode value = mapper.readTree(window);
            if (value == null || !value.isObject())
                throw new IllegalArgumentException("schedule window must be an object");
            if (value.has("enabled") && !value.get("enabled").isBoolean())
                throw new IllegalArgumentException("window enabled must be boolean");
            if (value.hasNonNull("timezone")) ZoneId.of(requiredText(value.get("timezone")));
            if (value.hasNonNull("startTime") || value.hasNonNull("endTime")) {
                // 每日窗口允许跨午夜及全天；它不是要求start<end的绝对日期范围。
                LocalTime.parse(requiredText(value.get("startTime")));
                LocalTime.parse(requiredText(value.get("endTime")));
            }
            if (value.hasNonNull("daysOfWeek")) {
                JsonNode days = value.get("daysOfWeek");
                if (!days.isArray() || days.size() > 7)
                    throw new IllegalArgumentException("invalid window days");
                if (!days.isEmpty()
                        && value.path("enabled").asBoolean(true)
                        && !value.hasNonNull("startTime"))
                    throw new IllegalArgumentException("window days require daily time bounds");
                for (JsonNode day : days)
                    DayOfWeek.valueOf(requiredText(day).toUpperCase(Locale.ROOT));
            }
        } catch (DateTimeException invalid) {
            throw new IllegalArgumentException("invalid schedule window time", invalid);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("invalid strategy schedule window", failure);
        }
        return new StrategyScheduleCreationSpec(
                strategy, type, cron, zone, raw.enabled(), window, dedup);
    }

    private static String requiredText(JsonNode value) {
        if (value == null || !value.isTextual() || value.asText().isBlank())
            throw new IllegalArgumentException("window text field required");
        return value.asText().trim();
    }

    private static String text(String value, int limit) {
        if (value == null
                || value.isBlank()
                || value.length() > limit
                || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("invalid schedule creation field");
        return value.trim();
    }
}
