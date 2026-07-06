package com.guidinglight.nexusquant.strategy.application.shadowrun;

/**
 * Shadow Run runner skeleton 的本地步骤枚举。
 *
 * <p>步骤只用于审计和测试 runner skeleton 的同步执行路径，不代表 scheduler、后台任务或真实
 * Shadow Live trading 已启动。
 */
public enum ShadowRunRunnerStep {
    CREATE_RUN,
    PRECHECKING,
    NO_SIDE_EFFECT_GUARD,
    READY,
    INPUT_MARKETDATA_SNAPSHOT,
    RUNNING,
    STRATEGY_DECISION_SNAPSHOT,
    RISK_PREFLIGHT_SNAPSHOT,
    ORDER_INTENT_PREVIEW_SNAPSHOT,
    COMPLETED,
    BLOCKED,
    FAILED,
    IDEMPOTENT_REPLAY
}
