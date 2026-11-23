package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.StrategyVersionStatus;

import java.util.Locale;

/** 所属策略编码始终精确限定；名称按字面子串查找，不把版本状态解释为运行或LIVE授权。 */
public record StrategyVersionListQuery(
        String strategyCode,
        String strategyVersionId,
        String versionName,
        Integer version,
        String status,
        int limit,
        int offset) {
    public StrategyVersionListQuery {
        strategyCode = normalize(strategyCode, 128);
        if (strategyCode == null)
            throw new IllegalArgumentException("strategyCode must not be blank");
        strategyVersionId = normalize(strategyVersionId, 128);
        versionName = normalize(versionName, 128);
        status = normalize(status, 32);
        if (status != null)
            status = StrategyVersionStatus.parse(status.toUpperCase(Locale.ROOT)).name();
        if (version != null && version < 1 || limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException("invalid strategy version number or page window");
        }
    }

    private static String normalize(String value, int maximum) {
        if (value == null) return null;
        // trim之前拒绝控制字符，避免把无效限定静默解释为无过滤查询。
        if (value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("invalid strategy version filter");
        if (value.isBlank()) return null;
        String text = value.trim();
        if (text.length() > maximum)
            throw new IllegalArgumentException("invalid strategy version filter");
        return text;
    }
}
