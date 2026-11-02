package com.guidinglight.nexusquant.strategy.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 计划配置身份和到期时刻定义一次消费；调用 UUID、dedup 展示范围和配置快照变化不能续发同一窗口。 */
public record StrategyDispatchIdentity(String scheduleJobId, String strategyId, Long accountId, Instant dueAt) {
    public StrategyDispatchIdentity {
        if (scheduleJobId == null || scheduleJobId.isBlank() || strategyId == null || strategyId.isBlank()
                || accountId == null || accountId <= 0) {
            throw new IllegalArgumentException("invalid strategy dispatch identity");
        }
        Objects.requireNonNull(dueAt, "dueAt");
        if (dueAt.getNano() != 0) throw new IllegalArgumentException("CRON dueAt must have whole-second precision");
    }

    /** 历史 scan 的三种确定性请求格式；仅用于保守拒绝旧已消费窗口，不猜测或回填旧 run。 */
    public List<String> legacyRequestIds() {
        String prefix = "req-schedule-" + scheduleJobId;
        String bucket = Long.toString(dueAt.toEpochMilli());
        return List.of(prefix + "-window-" + bucket, prefix + "-request-" + bucket,
                prefix + "-strategy-" + strategyId + "-" + bucket);
    }
}
