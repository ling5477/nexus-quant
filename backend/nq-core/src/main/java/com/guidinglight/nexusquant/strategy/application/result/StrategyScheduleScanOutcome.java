package com.guidinglight.nexusquant.strategy.application;

/**
 * StrategyScheduleScanOutcome 描述一次 schedule scan 对单条计划的最终决策。
 * <p>
 * Why:
 * GateE-2.2 需要把 window / dedup / busy / disabled / not_due 等门禁结果结构化返回，
 * 不能再只靠布尔值和自由文本 reason。
 */
public enum StrategyScheduleScanOutcome {
    TRIGGERED,
    SKIPPED_WINDOW,
    SKIPPED_DEDUP,
    SKIPPED_DISABLED,
    SKIPPED_STRATEGY_DISABLED,
    SKIPPED_BUSY,
    SKIPPED_NOT_DUE,
    FAILED
}


