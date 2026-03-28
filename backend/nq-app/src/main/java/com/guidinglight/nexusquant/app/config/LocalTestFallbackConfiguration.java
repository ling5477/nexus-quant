package com.guidinglight.nexusquant.app.config;

import com.guidinglight.nexusquant.adapter.api.service.AccountAdapter;
import com.guidinglight.nexusquant.adapter.api.service.MarketDataAdapter;
import com.guidinglight.nexusquant.adapter.api.service.NoopAccountAdapter;
import com.guidinglight.nexusquant.adapter.api.service.NoopMarketDataAdapter;
import com.guidinglight.nexusquant.config.service.ConfigSnapshotService;
import com.guidinglight.nexusquant.config.service.InMemoryConfigSnapshotService;
import com.guidinglight.nexusquant.ledger.service.LedgerService;
import com.guidinglight.nexusquant.ledger.service.NoopLedgerService;
import com.guidinglight.nexusquant.scheduler.service.PaperTradingAdapter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * LocalTestFallbackConfiguration 只负责 local/test/gated-verify 环境下的 fallback 装配。
 * <p>
 * 这些 Bean 明确属于 local/test/fallback only，不允许作为正式 profile 主路径。
 */
@Configuration
@Profile({"local", "test", "gated-verify"})
public class LocalTestFallbackConfiguration {

    @Bean
    public LedgerService ledgerService() {
        return new NoopLedgerService();
    }

    @Bean
    public ConfigSnapshotService configSnapshotService() {
        return new InMemoryConfigSnapshotService();
    }

    @Bean
    public PaperTradingAdapter paperTradingAdapter() {
        return new PaperTradingAdapter();
    }

    @Bean
    public MarketDataAdapter paperMarketDataAdapter() {
        return new NoopMarketDataAdapter("PAPER");
    }

    @Bean
    public MarketDataAdapter okxMarketDataAdapter() {
        return new NoopMarketDataAdapter("OKX");
    }

    @Bean
    public MarketDataAdapter binanceMarketDataAdapter() {
        return new NoopMarketDataAdapter("BINANCE");
    }

    @Bean
    public AccountAdapter paperAccountAdapter() {
        return new NoopAccountAdapter("PAPER");
    }

    @Bean
    public AccountAdapter okxAccountAdapter() {
        return new NoopAccountAdapter("OKX");
    }

    @Bean
    public AccountAdapter binanceAccountAdapter() {
        return new NoopAccountAdapter("BINANCE");
    }
}
