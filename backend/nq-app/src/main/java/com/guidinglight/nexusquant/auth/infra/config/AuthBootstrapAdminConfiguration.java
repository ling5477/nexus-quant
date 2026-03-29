package com.guidinglight.nexusquant.auth.infra.config;

import com.guidinglight.nexusquant.auth.application.AuthSeedService;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AuthBootstrapAdminConfiguration 提供显式 bootstrap admin 命令入口。
 */
@Configuration
@ConditionalOnProperty(prefix = "nq.auth.bootstrap-admin", name = "enabled", havingValue = "true")
public class AuthBootstrapAdminConfiguration {

    @Bean
    public ApplicationRunner bootstrapAdminRunner(
            AuthSeedService authSeedService,
            @Value("${nq.auth.bootstrap-admin.username}") String username,
            @Value("${nq.auth.bootstrap-admin.password-hash}") String passwordHash
    ) {
        return args -> authSeedService.bootstrapAdmin(new SeedUserCommand(
                username,
                passwordHash,
                List.of("ADMIN", "OPERATOR", "VIEWER"),
                true
        ));
    }
}


