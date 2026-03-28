package com.guidinglight.nexusquant.app.config;

import com.guidinglight.nexusquant.backtest.port.HistoricalMarketDataPort;
import com.guidinglight.nexusquant.infra.marketdata.jdbc.JdbcHistoricalMarketDataPort;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MarketdataModuleConfiguration 负责 marketdata 正式读路径装配。
 */
@Configuration
public class MarketdataModuleConfiguration {

    @Bean
    @Primary
    public HistoricalMarketDataPort historicalMarketDataPort(JdbcTemplate jdbcTemplate) {
        return new JdbcHistoricalMarketDataPort(jdbcTemplate);
    }
}
