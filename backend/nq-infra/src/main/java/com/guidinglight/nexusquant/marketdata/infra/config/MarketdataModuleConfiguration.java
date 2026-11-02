package com.guidinglight.nexusquant.marketdata.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.marketdata.domain.port.BarProvenanceReadPort;
import com.guidinglight.nexusquant.marketdata.domain.port.HistoricalMarketDataPort;
import com.guidinglight.nexusquant.marketdata.domain.port.PriceEventSourceRepository;
import com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcBarProvenanceReadPort;
import com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcHistoricalMarketDataPort;
import com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcPriceEventSourceRepository;

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
    public BarProvenanceReadPort barProvenanceReadPort(
            JdbcTemplate jdbc, PriceEventSourceRepository sources, ObjectMapper mapper) {
        return new JdbcBarProvenanceReadPort(jdbc, sources, mapper);
    }

    @Bean
    public PriceEventSourceRepository priceEventSourceRepository(JdbcTemplate jdbc) {
        return new JdbcPriceEventSourceRepository(jdbc);
    }

    @Bean
    @Primary
    public HistoricalMarketDataPort historicalMarketDataPort(JdbcTemplate jdbcTemplate) {
        return new JdbcHistoricalMarketDataPort(jdbcTemplate);
    }
}
