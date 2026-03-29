package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.application.StrategyManualTriggerRequest;
import com.guidinglight.nexusquant.strategy.application.StrategyManualTriggerResult;

/**
 * StrategyTriggerGateway 抽象手动 / 计划触发共用的运行启动入口。
 */
public interface StrategyTriggerGateway {

    StrategyManualTriggerResult trigger(StrategyManualTriggerRequest request);
}


