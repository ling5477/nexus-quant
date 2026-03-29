package com.guidinglight.nexusquant.auth.application;

import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;

import java.util.Objects;
import java.util.Optional;

/**
 * CurrentUserProfileService 提供当前用户查询能力。
 */
public class CurrentUserProfileService {

    private final AuthUserRepository authUserRepository;

    public CurrentUserProfileService(AuthUserRepository authUserRepository) {
        this.authUserRepository = Objects.requireNonNull(authUserRepository, "authUserRepository must not be null");
    }

    public Optional<AuthUserProfile> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return authUserRepository.findByUsername(username);
    }
}

