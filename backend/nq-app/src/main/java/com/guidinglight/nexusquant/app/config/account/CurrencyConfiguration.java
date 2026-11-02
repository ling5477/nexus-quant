package com.guidinglight.nexusquant.app.config.account;

import com.guidinglight.nexusquant.common.currency.CurrencyRegistry;
import com.guidinglight.nexusquant.common.currency.CurrencyValuationService;
import com.guidinglight.nexusquant.currency.infra.JdbcCurrencyRegistry;

import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** 先完成Flyway再读取币种定义；估值服务只拥有派生计算，不拥有资金事实。 */
@Configuration(proxyBeanMethods = false)
public class CurrencyConfiguration {
    @Bean
    public CurrencyRegistry currencyRegistry(JdbcTemplate jdbc) {
        return new JdbcCurrencyRegistry(jdbc);
    }

    @Bean
    @DependsOnDatabaseInitialization
    public CurrencyValuationService currencyValuationService(CurrencyRegistry registry) {
        return new CurrencyValuationService(registry);
    }
}
