package com.guidinglight.nexusquant.strategy.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** 原创建输入保留完整窗口字符串；长度前缀防止换行、分隔符或字段拼接造成身份歧义。 */
public record StrategyScheduleCreationSpec(
        String strategyId,
        String scheduleType,
        String cronExpr,
        String timezone,
        boolean enabled,
        String windowConfig,
        String dedupScope) {
    public String fingerprint(long owner) {
        if (owner <= 0) throw new IllegalArgumentException("positive creation owner required");
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String value :
                    List.of(
                            "STRATEGY_SCHEDULE_CREATION_V1",
                            Long.toString(owner),
                            strategyId,
                            scheduleType,
                            cronExpr,
                            timezone,
                            Boolean.toString(enabled),
                            windowConfig,
                            dedupScope)) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }
}
