package com.guidinglight.nexusquant.account.infra.config;

import com.guidinglight.nexusquant.account.application.ExchangeAccountQueryService;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcExchangeAccountRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * AccountModuleConfiguration 负责 exchange account 域内 Bean 装配。
 */
@Configuration
public class AccountModuleConfiguration {

    @Bean
    public ExchangeAccountRepository exchangeAccountRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcExchangeAccountRepository(jdbcTemplate);
    }

    @Bean
    public ExchangeAccountQueryService exchangeAccountQueryService(ExchangeAccountRepository exchangeAccountRepository) {
        return new ExchangeAccountQueryService(exchangeAccountRepository);
    }
}
