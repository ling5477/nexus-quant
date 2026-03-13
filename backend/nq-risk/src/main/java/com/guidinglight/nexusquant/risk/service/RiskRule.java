package com.guidinglight.nexusquant.risk.service;

import com.guidinglight.nexusquant.risk.model.RiskContext;
import com.guidinglight.nexusquant.risk.model.RiskDecisionResult;

import java.util.Optional;

/**
 * RiskRule 抽象一条可排序、可审计的 pre-trade 风控规则。
 * <p>
 * Why:
 * GateD 要求风控在进入真实执行前统一注册并按固定顺序执行，不能继续把校验散落在 controller、core 或 adapter。
 */
public interface RiskRule {

    String ruleCode();

    String ruleName();

    int order();

    /**
     * 执行单条规则。
     *
     * @param context 风控上下文
     * @return 命中拒绝时返回标准化结果；放行时返回 empty
     */
    Optional<RiskDecisionResult> evaluate(RiskContext context);
}
