package com.guidinglight.nexusquant.auth.application.service;

import com.guidinglight.nexusquant.auth.application.exception.AdminNotInitializedException;
import com.guidinglight.nexusquant.auth.application.port.AuthService;

import com.guidinglight.nexusquant.auth.application.command.LoginRequest;
import com.guidinglight.nexusquant.auth.application.result.LoginResponse;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.security.token.model.TokenClaims;
import com.guidinglight.nexusquant.security.token.port.TokenService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.Clock;
import com.guidinglight.nexusquant.auth.domain.AuthSession;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.UUID;

/**
 * DbAuthService 使用 DB-backed users/roles 完成正式认证。
 */
public class DbAuthService implements AuthService {

    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuthUserRepository authUserRepository;
    private final String issuer;
    private final long accessTokenTtlSeconds;

    private final long idleTimeoutSeconds;
    private final long sessionMaxSeconds;
    private final Clock clock;

    public DbAuthService(
            TokenService tokenService,
            PasswordEncoder passwordEncoder,
            AuthUserRepository authUserRepository,
            String issuer,
            long accessTokenTtlSeconds,
            long idleTimeoutSeconds,
            long sessionMaxSeconds,
            Clock clock
    ) {
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
        this.authUserRepository = Objects.requireNonNull(authUserRepository, "authUserRepository must not be null");
        this.issuer = Objects.requireNonNull(issuer, "issuer must not be null");
        if (accessTokenTtlSeconds < 1 || idleTimeoutSeconds < 1 || sessionMaxSeconds < idleTimeoutSeconds) {
            throw new IllegalArgumentException("invalid session durations");
        }
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.idleTimeoutSeconds = idleTimeoutSeconds;
        this.sessionMaxSeconds = sessionMaxSeconds;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        if (!authUserRepository.hasAdminUser()) {
            throw new AdminNotInitializedException();
        }
        if (request == null || request.username() == null || request.password() == null) {
            throw new BadCredentialsException("invalid username or password");
        }
        AuthUserProfile userProfile = authUserRepository.findByUsernameForUpdate(request.username())
                .orElseThrow(() -> new BadCredentialsException("invalid username or password"));
        if (!passwordEncoder.matches(request.password(), userProfile.passwordHash())) {
            throw new BadCredentialsException("invalid username or password");
        }
        if (!userProfile.enabled()) {
            throw new DisabledException("account is disabled");
        }
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var session = new AuthSession(UUID.randomUUID().toString(), userProfile.userId(), userProfile.authVersion(),
                now, now, now.plusSeconds(sessionMaxSeconds));
        authUserRepository.createSession(session, now.minusSeconds(idleTimeoutSeconds));
        return response(userProfile, session, now);
    }

    @Override
    @Transactional
    public LoginResponse renew(TokenClaims claims) {
        if (claims.sessionId() == null) {
            throw new BadCredentialsException("sign in again to start a renewable session");
        }
        // 与改密及注销按同一用户行锁串行；过期或已注销会话不能通过续期复活。
        var user = authUserRepository.findByUsernameForUpdate(claims.username())
                .filter(value -> value.enabled() && value.authVersion() == claims.authVersion()
                        && value.username().equals(claims.subject()) && !value.mustChangePassword())
                .orElseThrow(() -> new BadCredentialsException("authentication required"));
        Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        if (!claims.expiresAt().isAfter(now)) throw new BadCredentialsException("access token expired");
        var session = authUserRepository.renewSession(claims.sessionId(), user.userId(), user.authVersion(),
                        now, now.minusSeconds(idleTimeoutSeconds))
                .orElseThrow(() -> new BadCredentialsException("session expired or revoked"));
        return response(user, session, now);
    }

    @Override
    @Transactional
    public void logout(TokenClaims claims) {
        var user = authUserRepository.findByUsernameForUpdate(claims.username())
                .filter(value -> value.authVersion() == claims.authVersion())
                .orElseThrow(() -> new BadCredentialsException("authentication required"));
        if (claims.sessionId() == null) {
            // 升级前的无会话令牌只能通过代际撤销；它们不能进入续期链。
            authUserRepository.revokeUserTokens(user.userId(), user.authVersion());
        } else {
            authUserRepository.revokeSession(claims.sessionId(), user.userId());
        }
    }

    private LoginResponse response(AuthUserProfile user, AuthSession session, Instant now) {
        Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds);
        if (expiresAt.isAfter(session.expiresAt())) expiresAt = session.expiresAt();
        Instant idleExpiresAt = session.lastActiveAt().plusSeconds(idleTimeoutSeconds);
        if (idleExpiresAt.isAfter(session.expiresAt())) idleExpiresAt = session.expiresAt();
        TokenClaims claims = new TokenClaims(user.username(), user.username(), user.roles(), now, expiresAt,
                issuer, "jti-" + UUID.randomUUID(), user.authVersion(), session.sessionId());
        return new LoginResponse(tokenService.issue(claims), "Bearer", expiresAt.getEpochSecond() - now.getEpochSecond(),
                expiresAt, user.username(), user.roles(), user.mustChangePassword(), session.sessionId(),
                idleExpiresAt, session.expiresAt(), now);
    }
}
