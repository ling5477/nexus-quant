package com.guidinglight.nexusquant.core.service.port;

import com.guidinglight.nexusquant.contracts.model.RiskDecision;
import com.guidinglight.nexusquant.contracts.model.RiskSeverity;

/**
 * RiskEventRepository 定义风险事件落库端口。
 */
public interface RiskEventRepository {

    /**
     * 记录一次风控判定事件。
     *
     * @param scope 风险作用域
     * @param scopeId 作用域对象 ID
     * @param decision 判定结果
     * @param reason 判定原因
     * @param severity 风险级别
     * @param traceId 链路追踪 ID
     */
    void append(
            String scope,
            String scopeId,
            RiskDecision decision,
            String reason,
            RiskSeverity severity,
            String traceId
    );
}
