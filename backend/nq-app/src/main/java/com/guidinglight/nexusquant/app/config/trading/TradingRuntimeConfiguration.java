package com.guidinglight.nexusquant.app.config.trading;

import com.guidinglight.nexusquant.trading.domain.state.InMemoryOrderStateMachine;
import com.guidinglight.nexusquant.trading.domain.state.OrderStateMachine;
import com.guidinglight.nexusquant.risk.application.rule.AccountTradingEnabledRule;
import com.guidinglight.nexusquant.risk.application.rule.DuplicateRequestRule;
import com.guidinglight.nexusquant.risk.application.rule.KillSwitchRiskRule;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.application.port.KillSwitchStateRepository;
import com.guidinglight.nexusquant.risk.application.rule.MaxOrderAmountRule;
import com.guidinglight.nexusquant.risk.application.rule.MinNotionalRule;
import com.guidinglight.nexusquant.risk.application.rule.OrderPrecisionRule;
import com.guidinglight.nexusquant.risk.service.PreTradeRiskService;
import com.guidinglight.nexusquant.risk.application.config.PreTradeRiskSettings;
import com.guidinglight.nexusquant.risk.application.rule.RateLimitRule;
import com.guidinglight.nexusquant.risk.application.port.RiskGate;
import com.guidinglight.nexusquant.risk.application.rule.RiskRuleRegistry;
import com.guidinglight.nexusquant.risk.application.rule.SymbolEnabledRule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

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
    public KillSwitchService killSwitchService(KillSwitchStateRepository repository) {
        return new KillSwitchService(repository, Clock.systemUTC());
    }

    @Bean
    public RiskGate riskGate(KillSwitchService killSwitchService, PreTradeRiskSettings preTradeRiskSettings) {
        return new PreTradeRiskService(new RiskRuleRegistry(List.of(
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
