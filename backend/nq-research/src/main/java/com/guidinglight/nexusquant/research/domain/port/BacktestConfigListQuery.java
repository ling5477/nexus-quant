package com.guidinglight.nexusquant.research.domain.port;

/** 回测配置筛选在数据库执行；读取窗口和文本长度必须有界，控制字符不能被trim吞掉。 */
public record BacktestConfigListQuery(
        String researchConfigId, String backtestConfigId, String name, int limit, int offset) {
    public BacktestConfigListQuery {
        researchConfigId = normalize(researchConfigId, 128);
        backtestConfigId = normalize(backtestConfigId, 128);
        name = normalize(name, 255);
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException("invalid backtest configuration page window");
        }
    }

    private static String normalize(String value, int maximum) {
        if (value == null) return null;
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("invalid backtest configuration filter");
        }
        if (value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException("invalid backtest configuration filter");
        }
        return normalized;
    }
}
