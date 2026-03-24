package com.guidinglight.nexusquant.core.service;

import com.guidinglight.nexusquant.core.service.port.StrategyTriggerGateway;

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
