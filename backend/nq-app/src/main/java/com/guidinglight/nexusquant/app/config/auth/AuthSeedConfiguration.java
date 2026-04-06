package com.guidinglight.nexusquant.app.config.auth;

import com.guidinglight.nexusquant.auth.application.AuthSeedService;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;

import java.util.List;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * AuthSeedConfiguration 仅在 local/test 下把 seed users 写入数据库。
 */
@Configuration
@Profile({"local", "test"})
public class AuthSeedConfiguration {

    @Bean
    public ApplicationRunner authSeedRunner(SecurityRuntimeProperties properties, AuthSeedService authSeedService) {
        return args -> authSeedService.seedUsers(properties.getUsers().stream()
                .map(user -> new SeedUserCommand(
                        user.getUsername(),
                        user.getPasswordHash(),
                        List.copyOf(user.getRoles()),
                        user.isEnabled()
                ))
                .toList());
    }
}


