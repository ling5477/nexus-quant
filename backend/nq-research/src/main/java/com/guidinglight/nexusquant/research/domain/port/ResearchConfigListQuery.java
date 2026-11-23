package com.guidinglight.nexusquant.research.domain.port;

/** 研究列表的服务端筛选与读取窗口；业务选择面不能先读取全表再在浏览器筛选。 */
public record ResearchConfigListQuery(
        String sourceStrategyId, String researchConfigId, String name, int limit, int offset) {
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    public ResearchConfigListQuery {
        sourceStrategyId = normalize(sourceStrategyId, 128);
        researchConfigId = normalize(researchConfigId, 128);
        name = normalize(name, 255);
        if (limit < 1 || limit > MAX_LIMIT || offset < 0) {
            throw new IllegalArgumentException("invalid research configuration page window");
        }
    }

    private static String normalize(String value, int maximum) {
        if (value == null) return null;
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("invalid research configuration filter");
        }
        if (value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException("invalid research configuration filter");
        }
        return normalized;
    }
}
