package com.guidinglight.nexusquant.strategy.domain;

/**
 * StrategyVersionStatus 定义 GateI-1 策略版本生命周期。
 *
 * Why:
 * 策略定义的 enabled 表示运行开关，不能承担“可发布版本”的审计语义。
 * GateI-1 用独立状态表达版本是否仍在草稿、是否可被发布引用、是否已归档。
 */
public enum StrategyVersionStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED;

    /**
     * 解析外部输入的版本状态。
     *
     * @param value 外部状态文本，大小写不敏感，不允许为空
     * @return 标准策略版本状态
     * @throws IllegalArgumentException 当状态为空或不在 DRAFT / ACTIVE / ARCHIVED 范围内时抛出
     */
    public static StrategyVersionStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("status must not be blank");
        }
        return StrategyVersionStatus.valueOf(value.trim().toUpperCase());
    }
}
