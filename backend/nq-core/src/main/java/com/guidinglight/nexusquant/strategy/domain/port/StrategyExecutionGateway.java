package com.guidinglight.nexusquant.strategy.domain.port;

/**
 * StrategyExecutionGateway 定义 Strategy 到 execution capability 的稳定桥接。
 */
public interface StrategyExecutionGateway {

    StrategyExecutionResult execute(StrategyExecutionIntent intent);

    /** 仅凭原 run 身份加载持久 work；实现缺失时不能回退到生成新请求。 */
    default StrategyExecutionResult resume(String runId) {
        throw new UnsupportedOperationException("durable same-run execution is not implemented");
    }
}


