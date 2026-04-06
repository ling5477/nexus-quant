package com.guidinglight.nexusquant.app.config.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.account.application.ExchangeAccountCredentialCommandService;
import com.guidinglight.nexusquant.account.application.ExchangeAccountCredentialVerificationService;
import com.guidinglight.nexusquant.account.application.ExchangeAccountQueryService;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialVerifier;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.verification.StructuralExchangeAccountCredentialVerifier;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * AccountModuleConfiguration 负责 exchange account 域内 Bean 装配。
 */
@Configuration
@EnableConfigurationProperties(AccountCredentialRuntimeProperties.class)
public class AccountModuleConfiguration {

    @Bean
    public ExchangeAccountRepository exchangeAccountRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcExchangeAccountRepository(jdbcTemplate);
    }

    @Bean
    public ExchangeAccountQueryService exchangeAccountQueryService(ExchangeAccountRepository exchangeAccountRepository) {
        return new ExchangeAccountQueryService(exchangeAccountRepository);
    }

    @Bean
    public ExchangeAccountCommandService exchangeAccountCommandService(ExchangeAccountRepository exchangeAccountRepository) {
        return new ExchangeAccountCommandService(exchangeAccountRepository);
    }

    @Bean
    public ExchangeAccountCredentialRepository exchangeAccountCredentialRepository(
            JdbcTemplate jdbcTemplate,
            AccountCredentialRuntimeProperties properties
    ) {
        return new JdbcExchangeAccountCredentialRepository(jdbcTemplate, properties.getMasterKey());
    }

    @Bean
    public ExchangeAccountCredentialVerifier exchangeAccountCredentialVerifier(
            ObjectMapper objectMapper,
            AccountCredentialRuntimeProperties properties
    ) {
        return new StructuralExchangeAccountCredentialVerifier(objectMapper, properties.getVerificationMode());
    }

    @Bean
    public ExchangeAccountCredentialCommandService exchangeAccountCredentialCommandService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository exchangeAccountCredentialRepository,
            ObjectMapper objectMapper
    ) {
        return new ExchangeAccountCredentialCommandService(
                exchangeAccountRepository,
                exchangeAccountCredentialRepository,
                objectMapper
        );
    }

    @Bean
    public ExchangeAccountCredentialVerificationService exchangeAccountCredentialVerificationService(
            ExchangeAccountRepository exchangeAccountRepository,
            ExchangeAccountCredentialRepository exchangeAccountCredentialRepository,
            ExchangeAccountCredentialVerifier exchangeAccountCredentialVerifier
    ) {
        return new ExchangeAccountCredentialVerificationService(
                exchangeAccountRepository,
                exchangeAccountCredentialRepository,
                exchangeAccountCredentialVerifier
        );
    }
}
