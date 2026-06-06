package com.guidinglight.nexusquant.strategy.application.port;

import com.guidinglight.nexusquant.strategy.application.StrategyManualTriggerRequest;
import com.guidinglight.nexusquant.strategy.application.StrategyManualTriggerResult;
import com.guidinglight.nexusquant.strategy.application.StrategyManualTriggerService;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyTriggerGateway;

import java.util.Objects;

import org.springframework.stereotype.Component;

/**
 * ManualStrategyTriggerGateway 复用 GateE-1.2 的手动 trigger 主链给 schedule 入口调用。
 */
@Component
public class ManualStrategyTriggerGateway implements StrategyTriggerGateway {

    private final StrategyManualTriggerService strategyManualTriggerService;

    public ManualStrategyTriggerGateway(StrategyManualTriggerService strategyManualTriggerService) {
        this.strategyManualTriggerService = Objects.requireNonNull(
                strategyManualTriggerService,
                "strategyManualTriggerService must not be null"
        );
    }

    @Override
    public StrategyManualTriggerResult trigger(StrategyManualTriggerRequest request) {
        return strategyManualTriggerService.trigger(request);
    }
}



