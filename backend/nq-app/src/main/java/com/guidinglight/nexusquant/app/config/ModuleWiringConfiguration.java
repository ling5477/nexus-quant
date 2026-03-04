package com.guidinglight.nexusquant.app.config;

import com.guidinglight.nexusquant.adapter.api.service.AccountAdapter;
import com.guidinglight.nexusquant.adapter.api.service.MarketDataAdapter;
import com.guidinglight.nexusquant.adapter.api.service.NoopAccountAdapter;
import com.guidinglight.nexusquant.adapter.api.service.NoopMarketDataAdapter;
import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceExchangeAdapter;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.api.service.NoopTradingQueryFacade;
import com.guidinglight.nexusquant.api.service.TradingQueryFacade;
import com.guidinglight.nexusquant.auth.service.AuthService;
import com.guidinglight.nexusquant.auth.service.NoopAuthService;
import com.guidinglight.nexusquant.config.service.ConfigSnapshotService;
import com.guidinglight.nexusquant.config.service.InMemoryConfigSnapshotService;
import com.guidinglight.nexusquant.core.recovery.RecoveryService;
import com.guidinglight.nexusquant.core.state.InMemoryOrderStateMachine;
import com.guidinglight.nexusquant.core.state.OrderStateMachine;
import com.guidinglight.nexusquant.gateway.service.GatewayAuthFacade;
import com.guidinglight.nexusquant.gateway.service.NoopGatewayAuthFacade;
import com.guidinglight.nexusquant.ledger.service.LedgerService;
import com.guidinglight.nexusquant.ledger.service.NoopLedgerService;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.NoopRiskGate;
import com.guidinglight.nexusquant.risk.service.RiskGate;
import com.guidinglight.nexusquant.scheduler.service.PaperTradingAdapter;
import com.guidinglight.nexusquant.scheduler.service.OkxRecoveryService;
import com.guidinglight.nexusquant.security.service.StubTokenService;
import com.guidinglight.nexusquant.security.service.TokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ModuleWiringConfiguration 负责模块级占位 Bean 装配。
 * <p>
 * Why:
 * Gate A 要求工程“可启动但空业务”，因此需要在启动模块提供可运行默认实现，
 * 后续各模块实现替换时只需覆写对应 Bean。
 */
@Configuration
public class ModuleWiringConfiguration {

    @Bean
    public OrderStateMachine orderStateMachine() {
        return new InMemoryOrderStateMachine();
    }

    @Bean
    public RecoveryService recoveryService(OkxRecoveryService okxRecoveryService) {
        return okxRecoveryService;
    }

    @Bean
    public LedgerService ledgerService() {
        return new NoopLedgerService();
    }

    @Bean
    public RiskGate riskGate() {
        return new NoopRiskGate();
    }

    @Bean
    public KillSwitchService killSwitchService() {
        return new KillSwitchService();
    }

    @Bean
    public ConfigSnapshotService configSnapshotService() {
        return new InMemoryConfigSnapshotService();
    }

    @Bean
    public TokenService tokenService() {
        return new StubTokenService();
    }

    @Bean
    public AuthService authService(TokenService tokenService) {
        return new NoopAuthService(tokenService);
    }

    @Bean
    public GatewayAuthFacade gatewayAuthFacade() {
        return new NoopGatewayAuthFacade();
    }

    @Bean
    public TradingQueryFacade tradingQueryFacade() {
        return new NoopTradingQueryFacade();
    }

    /**
     * 提供 PAPER 的交易端口实现。
     */
    @Bean
    public PaperTradingAdapter paperTradingAdapter() {
        return new PaperTradingAdapter();
    }

    /**
     * 提供 OKX 的交易端口实现。
     */
    @Bean
    public OkxExchangeAdapter okxTradingAdapter() {
        return new OkxExchangeAdapter();
    }

    /**
     * 提供 Binance 的交易端口实现。
     */
    @Bean
    public BinanceExchangeAdapter binanceTradingAdapter() {
        return new BinanceExchangeAdapter();
    }

    /**
     * 提供 PAPER 的行情端口 stub。
     */
    @Bean
    public MarketDataAdapter paperMarketDataAdapter() {
        return new NoopMarketDataAdapter("PAPER");
    }

    /**
     * 提供 OKX 的行情端口 stub。
     */
    @Bean
    public MarketDataAdapter okxMarketDataAdapter() {
        return new NoopMarketDataAdapter("OKX");
    }

    /**
     * 提供 Binance 的行情端口 stub。
     */
    @Bean
    public MarketDataAdapter binanceMarketDataAdapter() {
        return new NoopMarketDataAdapter("BINANCE");
    }

    /**
     * 提供 PAPER 的账户端口 stub。
     */
    @Bean
    public AccountAdapter paperAccountAdapter() {
        return new NoopAccountAdapter("PAPER");
    }

    /**
     * 提供 OKX 的账户端口 stub。
     */
    @Bean
    public AccountAdapter okxAccountAdapter() {
        return new NoopAccountAdapter("OKX");
    }

    /**
     * 提供 Binance 的账户端口 stub。
     */
    @Bean
    public AccountAdapter binanceAccountAdapter() {
        return new NoopAccountAdapter("BINANCE");
    }
}
