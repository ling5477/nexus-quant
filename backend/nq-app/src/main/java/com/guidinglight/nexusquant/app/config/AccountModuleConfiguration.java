package com.guidinglight.nexusquant.app.config;

import com.guidinglight.nexusquant.api.service.TradingQueryFacade;
import com.guidinglight.nexusquant.app.trading.query.JdbcTradingQueryFacade;
import com.guidinglight.nexusquant.core.account.application.ExchangeAccountQueryService;
import com.guidinglight.nexusquant.core.account.application.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.core.recovery.RecoveryService;
import com.guidinglight.nexusquant.core.service.TradingMaintenanceService;
import com.guidinglight.nexusquant.core.state.InMemoryOrderStateMachine;
import com.guidinglight.nexusquant.core.state.OrderStateMachine;
import com.guidinglight.nexusquant.infra.account.jdbc.JdbcExchangeAccountRepository;
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
 * AccountModuleConfiguration 负责 exchange account 相关最小装配。
 */
@Configuration
public class AccountModuleConfiguration {

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
    public ExchangeAccountRepository exchangeAccountRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcExchangeAccountRepository(jdbcTemplate);
    }

    @Bean
    public ExchangeAccountQueryService exchangeAccountQueryService(ExchangeAccountRepository exchangeAccountRepository) {
        return new ExchangeAccountQueryService(exchangeAccountRepository);
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
