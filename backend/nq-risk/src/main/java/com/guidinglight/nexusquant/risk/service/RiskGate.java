package com.guidinglight.nexusquant.risk.service;

import com.guidinglight.nexusquant.risk.model.RiskContext;
import com.guidinglight.nexusquant.risk.model.RiskDecisionResult;

/**
 * RiskGate 定义事前风控判定接口。
 */
public interface RiskGate {

    /**
     * 对下单请求执行风控判定。
     *
     * @param context 风控上下文
     * @return ALLOW/REJECT 及原因
     */
    RiskDecisionResult evaluate(RiskContext context);
}
