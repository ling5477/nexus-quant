package com.guidinglight.nexusquant.app.config;

import com.guidinglight.nexusquant.scheduler.integration.PaperTradingAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** 隔离策略 SIM 明确启用时装配本地撮合适配器；不连接交易所。 */
@Configuration
@Profile("!local & !test")
@ConditionalOnProperty(prefix = "nq.strategy-sim", name = "enabled", havingValue = "true")
public class StrategySimAdapterConfiguration {
    @Bean
    public PaperTradingAdapter paperTradingAdapter() {
        return new PaperTradingAdapter();
    }
}
