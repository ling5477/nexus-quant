package com.guidinglight.nexusquant.scheduler.scheduling;

import com.guidinglight.nexusquant.strategy.application.StrategyRunRecoveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 供统一 Dispatcher 调用的单轮恢复入口；数据库扫描游标负责跨重启公平性。 */
@Component
@ConditionalOnProperty(name = "nq.runtime.trading-components.enabled", havingValue = "true")
public class StrategyRunRecoveryTick {
    private final StrategyRunRecoveryService recovery;

    public StrategyRunRecoveryTick(StrategyRunRecoveryService recovery) {
        this.recovery = recovery;
    }

    public void tick() {
        recovery.recoverAll();
    }

}
