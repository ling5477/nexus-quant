package com.guidinglight.nexusquant.strategy.infra.jdbc;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 将同一冻结时刻无损拆为 PostgreSQL 微秒和不足一微秒的余数。
 *
 * <p>写入前必须向下取微秒，不能让驱动四舍五入后再叠加余数；该规则也适用于纪元之前的时间。
 * 三个正式 Shadow 读取方共用还原规则，幂等比较继续使用完整 Instant，不放宽为微秒相等。
 */
final class ShadowRunWindowPrecision {
    private ShadowRunWindowPrecision() {}

    static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value.truncatedTo(ChronoUnit.MICROS));
    }

    static int remainder(Instant value) {
        return value == null ? 0 : value.getNano() % 1_000;
    }

    static Instant restore(Timestamp timestamp, int remainder) {
        if (remainder < 0 || remainder > 999 || (timestamp == null && remainder != 0)) {
            throw new IllegalStateException("invalid persisted Shadow window remainder");
        }
        return timestamp == null ? null : timestamp.toInstant().plusNanos(remainder);
    }
}
