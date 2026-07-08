package com.guidinglight.nexusquant.strategy.domain.port;

/**
 * StrategyValidationOverviewQueryPort 是 GateS-3 strategy validation overview 的只读查询端口。
 *
 * <p>该端口只允许读取本地 strategy/evaluation/publish/Paper/Shadow 事实，不提供写库、runner、
 * scheduler、adapter、credential、order、account 或 ledger 能力。
 */
public interface StrategyValidationOverviewQueryPort {

    /**
     * 读取 Strategy Validation overview 所需事实。
     *
     * @return SELECT-only overview facts；空表必须返回稳定空结构，不得抛 500
     */
    StrategyValidationOverviewFacts loadOverviewFacts();
}
