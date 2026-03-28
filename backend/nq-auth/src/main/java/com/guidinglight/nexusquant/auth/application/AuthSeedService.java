package com.guidinglight.nexusquant.auth.application;

import com.guidinglight.nexusquant.auth.application.port.AuthUserRepository;

import java.util.List;
import java.util.Objects;

/**
 * AuthSeedService 统一处理 local/test seed 与显式 bootstrap admin。
 */
public class AuthSeedService {

    private final AuthUserRepository authUserRepository;

    public AuthSeedService(AuthUserRepository authUserRepository) {
        this.authUserRepository = Objects.requireNonNull(authUserRepository, "authUserRepository must not be null");
    }

    public void seedUsers(List<SeedUserCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        commands.forEach(authUserRepository::upsertSeedUser);
    }

    public void bootstrapAdmin(SeedUserCommand command) {
        authUserRepository.upsertSeedUser(command);
    }
}
