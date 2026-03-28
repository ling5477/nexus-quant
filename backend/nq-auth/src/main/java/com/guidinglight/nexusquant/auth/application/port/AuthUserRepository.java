package com.guidinglight.nexusquant.auth.application.port;

import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.auth.application.SeedUserCommand;

import java.util.Optional;

/**
 * AuthUserRepository 定义认证用户读写端口。
 */
public interface AuthUserRepository {

    Optional<AuthUserProfile> findByUsername(String username);

    boolean hasAdminUser();

    void upsertSeedUser(SeedUserCommand command);
}
