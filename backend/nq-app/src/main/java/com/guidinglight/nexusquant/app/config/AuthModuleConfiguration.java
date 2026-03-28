package com.guidinglight.nexusquant.app.config;

import com.guidinglight.nexusquant.auth.application.AuthSeedService;
import com.guidinglight.nexusquant.auth.application.port.AuthUserRepository;
import com.guidinglight.nexusquant.infra.auth.jdbc.JdbcAuthUserRepository;

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
}
