package com.guidinglight.nexusquant.app.config;

import com.guidinglight.nexusquant.adapter.api.service.port.AccountAdapter;
import com.guidinglight.nexusquant.adapter.api.service.port.AdapterReadinessService;
import com.guidinglight.nexusquant.adapter.api.service.readiness.DefaultAdapterReadinessService;
import com.guidinglight.nexusquant.adapter.api.service.port.MarketDataAdapter;
import com.guidinglight.nexusquant.adapter.api.service.compatibility.NoopAccountAdapter;
import com.guidinglight.nexusquant.adapter.api.service.compatibility.NoopMarketDataAdapter;
import com.guidinglight.nexusquant.adapter.api.service.readiness.ReadinessGuardedAdapterFactory;
import com.guidinglight.nexusquant.config.service.ConfigSnapshotService;
import com.guidinglight.nexusquant.config.service.InMemoryConfigSnapshotService;
import com.guidinglight.nexusquant.scheduler.integration.PaperTradingAdapter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * LocalTestFallbackConfiguration 只负责 local/test 环境下的 fallback 装配。
 * <p>
 * Why:
 * 该配置仅供本地/测试兼容使用，
 * 这些 Bean 仅用于 local/test/fallback only，不允许作为正式 profile 主路径，也不应被后续规划当成正式模块能力。
 */
@Configuration
@Profile({"local", "test"})
public class LocalTestFallbackConfiguration {

    @Bean
    public ConfigSnapshotService configSnapshotService() {
        return new InMemoryConfigSnapshotService();
    }

    @Bean
    public PaperTradingAdapter paperTradingAdapter() {
        return new PaperTradingAdapter();
    }

    /**
     * 适配器就绪策略装配层 readiness 服务。
     * <p>
     * Why:
     * 行情 adapter 在本配置里被 readiness guard 包装，需要一个 readiness 评估服务。
     * DefaultAdapterReadinessService 是纯静态 fail-closed 策略，无 IO / credential / 网络，可安全装配。
     */
    @Bean
    public AdapterReadinessService adapterReadinessService() {
        return new DefaultAdapterReadinessService();
    }

    /**
     * 适配器就绪策略：行情 adapter 在装配层默认被 readiness guard 包装。
     * <p>
     * Why:
     * 让调用方拿到的 MarketDataAdapter 默认经过 readiness 守卫——readiness guard 为外层权威，
     * NoopMarketDataAdapter 为内层兜底（适配器就绪策略 P2-2 收敛）。当前 baseline 下 readiness 恒未就绪，
     * 因此订阅一律 fail-closed（subscribed=false），不会把 stub 误判为真实订阅成功。
     */
    @Bean
    public MarketDataAdapter paperMarketDataAdapter(AdapterReadinessService readinessService) {
        return ReadinessGuardedAdapterFactory.guarded(new NoopMarketDataAdapter("PAPER"), readinessService);
    }

    @Bean
    public MarketDataAdapter okxMarketDataAdapter(AdapterReadinessService readinessService) {
        return ReadinessGuardedAdapterFactory.guarded(new NoopMarketDataAdapter("OKX"), readinessService);
    }

    @Bean
    public MarketDataAdapter binanceMarketDataAdapter(AdapterReadinessService readinessService) {
        return ReadinessGuardedAdapterFactory.guarded(new NoopMarketDataAdapter("BINANCE"), readinessService);
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
