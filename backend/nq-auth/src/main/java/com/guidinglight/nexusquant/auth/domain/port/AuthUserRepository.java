package com.guidinglight.nexusquant.auth.domain.port;

import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;

import java.util.Optional;
import java.time.Instant;
import com.guidinglight.nexusquant.auth.domain.AuthSession;

/**
 * AuthUserRepository 定义认证用户读写端口。
 */
public interface AuthUserRepository {

    Optional<AuthUserProfile> findByUsername(String username);

    boolean hasAdminUser();

    void upsertSeedUser(SeedUserCommand command);

    boolean createInitialAdminIfAbsent(SeedUserCommand command);

    Optional<AuthUserProfile> findByUsernameForUpdate(String username);

    boolean changePassword(Long userId, long authVersion, String passwordHash);

    void createSession(AuthSession session, Instant idleCutoff);

    boolean isSessionActive(String sessionId, String username, long authVersion, Instant now, Instant idleCutoff);

    Optional<AuthSession> renewSession(String sessionId, long userId, long authVersion, Instant now, Instant idleCutoff);

    void revokeSession(String sessionId, long userId);

    void revokeUserTokens(long userId, long authVersion);
}


