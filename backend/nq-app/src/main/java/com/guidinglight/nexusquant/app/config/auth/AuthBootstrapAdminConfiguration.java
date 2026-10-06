package com.guidinglight.nexusquant.app.config.auth;

import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
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
            PasswordEncoder passwordEncoder
    ) {
        return args -> authSeedService.bootstrapAdmin(new SeedUserCommand(
                "admin",
                passwordEncoder.encode("123456"),
                List.of("ADMIN", "OPERATOR", "VIEWER"),
                true
        ));
    }
}


