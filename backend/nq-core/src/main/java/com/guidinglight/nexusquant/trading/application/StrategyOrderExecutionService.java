package com.guidinglight.nexusquant.trading.application;

import org.springframework.stereotype.Service;
import com.guidinglight.nexusquant.trading.domain.EffectiveOrderParameters;

/** 同 run 的普通执行编排；B 提交后复用原 V49/HTTP 边界。 */
@Service
public class StrategyOrderExecutionService {
    private final StrategyOrderPreparationService preparation;
    private final OrderCommandService commands;
    public StrategyOrderExecutionService(StrategyOrderPreparationService preparation, OrderCommandService commands) {
        this.preparation = preparation;
        this.commands = commands;
    }

    public PlaceOrderResult execute(String runId, boolean recovery, EffectiveOrderParameters parameters) {
        var prepared = preparation.prepare(runId, recovery, parameters);
        return commands.executePreparedPlaceOrder(prepared.request(), prepared.preparation());
    }
}
