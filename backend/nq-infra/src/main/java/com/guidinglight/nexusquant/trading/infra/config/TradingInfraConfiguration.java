package com.guidinglight.nexusquant.trading.infra.config;

import com.guidinglight.nexusquant.trading.application.query.TradingQueryFacade;
import com.guidinglight.nexusquant.trading.infra.query.JdbcTradingQueryFacade;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TradingInfraConfiguration 负责 trading 读侧 JDBC Bean 装配。
 * <p>
 * Why:
 * `JdbcTradingQueryFacade` 属于 `nq-infra` 的正式实现，运行时 Bean 的创建也应该随 owner 一起收口，
 * 避免 `nq-app` 继续 import infra concrete。
 */
@Configuration
public class TradingInfraConfiguration {

    @Bean
    public TradingQueryFacade tradingQueryFacade(JdbcTemplate jdbcTemplate) {
        return new JdbcTradingQueryFacade(jdbcTemplate);
    }
}
