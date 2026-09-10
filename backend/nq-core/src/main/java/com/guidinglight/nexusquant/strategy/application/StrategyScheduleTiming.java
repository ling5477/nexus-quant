package com.guidinglight.nexusquant.strategy.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.domain.StrategySchedule;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Locale;
import org.springframework.scheduling.support.CronExpression;

/** scanner 与持锁 admission 共用窗口解释，避免先后使用两种 due/cursor 规则。 */
public final class StrategyScheduleTiming {
    private static final ObjectMapper JSON = new ObjectMapper();

    private StrategyScheduleTiming() { }

    public static Instant nextDue(StrategySchedule schedule, Instant reference, Instant now) {
        if (!"CRON".equalsIgnoreCase(schedule.scheduleType())) return null;
        Instant start = reference == null ? schedule.createdAt().minusSeconds(1) : reference;
        var next = CronExpression.parse(schedule.cronExpr()).next(start.atZone(ZoneId.of(schedule.timezone())));
        return next == null || next.toInstant().isAfter(now) ? null : next.toInstant();
    }

    public static String blockedReason(StrategySchedule schedule, Instant now) {
        try {
            JsonNode config = JSON.readTree(schedule.windowConfig() == null || schedule.windowConfig().isBlank()
                    ? "{}" : schedule.windowConfig());
            if (config.isEmpty() || !config.path("enabled").asBoolean(true)
                    || !config.hasNonNull("startTime") && !config.hasNonNull("endTime")) return null;
            var current = now.atZone(ZoneId.of(config.path("timezone").asText(schedule.timezone())));
            JsonNode days = config.path("daysOfWeek");
            if (!days.isMissingNode() && !days.isEmpty()) {
                EnumSet<DayOfWeek> allowed = EnumSet.noneOf(DayOfWeek.class);
                days.forEach(day -> allowed.add(DayOfWeek.valueOf(day.asText().trim().toUpperCase(Locale.ROOT))));
                if (!allowed.contains(current.getDayOfWeek())) return "window_day_blocked";
            }
            LocalTime start = LocalTime.parse(config.path("startTime").asText());
            LocalTime end = LocalTime.parse(config.path("endTime").asText());
            LocalTime time = current.toLocalTime();
            boolean allowed = start.equals(end) || (start.isBefore(end)
                    ? !time.isBefore(start) && time.isBefore(end) : !time.isBefore(start) || time.isBefore(end));
            return allowed ? null : "window_closed";
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid strategy schedule window", ex);
        }
    }
}
