package com.guidinglight.nexusquant.app.config.trading;

import com.guidinglight.nexusquant.trading.domain.state.InMemoryOrderStateMachine;
import com.guidinglight.nexusquant.trading.domain.state.OrderStateMachine;
import com.guidinglight.nexusquant.risk.service.AccountTradingEnabledRule;
import com.guidinglight.nexusquant.risk.service.DuplicateRequestRule;
import com.guidinglight.nexusquant.risk.service.KillSwitchRiskRule;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.MaxOrderAmountRule;
import com.guidinglight.nexusquant.risk.service.MinNotionalRule;
import com.guidinglight.nexusquant.risk.service.OrderPrecisionRule;
import com.guidinglight.nexusquant.risk.service.PreTradeRiskService;
import com.guidinglight.nexusquant.risk.service.PreTradeRiskSettings;
import com.guidinglight.nexusquant.risk.service.RateLimitRule;
import com.guidinglight.nexusquant.risk.service.RiskGate;
import com.guidinglight.nexusquant.risk.service.RiskRuleRegistry;
import com.guidinglight.nexusquant.risk.service.SymbolEnabledRule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * TradingRuntimeConfiguration 负责 trading 域仍需保留在 composition root 的最小运行时装配。
 * <p>
 * Why:
 * PRE-1 要把 infra concrete 与 scheduler 具体实现从 `nq-app` 尽量移出去，
 * 这里仅保留状态机与风控默认值等真正属于 composition root 的 Bean。
 */
@Configuration
public class TradingRuntimeConfiguration {

    @Bean
    public OrderStateMachine orderStateMachine() {
        return new InMemoryOrderStateMachine();
    }

    @Bean
    public PreTradeRiskSettings preTradeRiskSettings() {
        return PreTradeRiskSettings.defaults();
    }

    @Bean
    public KillSwitchService killSwitchService() {
        return new KillSwitchService();
    }

    @Bean
    public RiskGate riskGate(KillSwitchService killSwitchService, PreTradeRiskSettings preTradeRiskSettings) {
        return new PreTradeRiskService(new RiskRuleRegistry(java.util.List.of(
                new KillSwitchRiskRule(killSwitchService),
                new AccountTradingEnabledRule(preTradeRiskSettings),
                new SymbolEnabledRule(preTradeRiskSettings),
                new DuplicateRequestRule(preTradeRiskSettings),
                new RateLimitRule(preTradeRiskSettings),
                new OrderPrecisionRule(preTradeRiskSettings),
                new MinNotionalRule(preTradeRiskSettings),
                new MaxOrderAmountRule(preTradeRiskSettings)
        )));
    }
}
