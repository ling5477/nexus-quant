package com.guidinglight.nexusquant.trading.infra.config;

import com.guidinglight.nexusquant.trading.application.query.TradingQueryFacade;
import com.guidinglight.nexusquant.trading.infra.query.JdbcTradingQueryFacade;
import com.guidinglight.nexusquant.trading.application.RecoveryService;
import com.guidinglight.nexusquant.trading.application.TradingMaintenanceService;
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
import com.guidinglight.nexusquant.scheduler.service.BinanceRecoveryService;
import com.guidinglight.nexusquant.scheduler.service.BinanceRestReconcileService;
import com.guidinglight.nexusquant.scheduler.service.OkxRecoveryService;
import com.guidinglight.nexusquant.scheduler.service.OkxRestReconcileService;
import com.guidinglight.nexusquant.scheduler.service.SchedulerTradingMaintenanceService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TradingRuntimeConfiguration 先承接 trading/risk/scheduler 运行时装配，避免继续堆在 account 配置中。
 */
@Configuration
public class TradingRuntimeConfiguration {

    @Bean
    public OrderStateMachine orderStateMachine() {
        return new InMemoryOrderStateMachine();
    }

    @Bean
    public RecoveryService recoveryService(OkxRecoveryService okxRecoveryService) {
        return okxRecoveryService;
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

    @Bean
    public TradingQueryFacade tradingQueryFacade(JdbcTemplate jdbcTemplate) {
        return new JdbcTradingQueryFacade(jdbcTemplate);
    }

    @Bean
    public TradingMaintenanceService tradingMaintenanceService(
            OkxRestReconcileService okxRestReconcileService,
            BinanceRestReconcileService binanceRestReconcileService,
            BinanceRecoveryService binanceRecoveryService,
            RecoveryService recoveryService
    ) {
        return new SchedulerTradingMaintenanceService(
                okxRestReconcileService,
                binanceRestReconcileService,
                binanceRecoveryService,
                recoveryService
        );
    }
}

