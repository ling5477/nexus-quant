package com.guidinglight.nexusquant.app.config.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.auth.application.service.UserPreferencesService;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.auth.domain.port.UserMarketCatalog;
import com.guidinglight.nexusquant.auth.domain.port.UserPreferencesRepository;
import com.guidinglight.nexusquant.auth.infra.jdbc.JdbcAuthUserRepository;
import com.guidinglight.nexusquant.auth.infra.jdbc.JdbcUserMarketCatalog;
import com.guidinglight.nexusquant.auth.infra.jdbc.JdbcUserPreferencesRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * AuthModuleConfiguration 负责 DB-backed auth 相关最小装配。
 */
@Configuration
public class AuthModuleConfiguration {

    @Bean
    public AuthUserRepository authUserRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcAuthUserRepository(jdbcTemplate);
    }

    @Bean
    public AuthSeedService authSeedService(AuthUserRepository authUserRepository) {
        return new AuthSeedService(authUserRepository);
    }

    @Bean
    public UserPreferencesRepository userPreferencesRepository(
            JdbcTemplate jdbc, ObjectMapper mapper) {
        return new JdbcUserPreferencesRepository(jdbc, mapper);
    }

    @Bean
    public UserMarketCatalog userMarketCatalog(JdbcTemplate jdbc) {
        return new JdbcUserMarketCatalog(jdbc);
    }

    @Bean
    public UserPreferencesService userPreferencesService(
            UserPreferencesRepository repository, UserMarketCatalog catalog) {
        return new UserPreferencesService(repository, catalog);
    }
}
