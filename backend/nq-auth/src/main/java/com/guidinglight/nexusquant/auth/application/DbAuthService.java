package com.guidinglight.nexusquant.auth.application;

import com.guidinglight.nexusquant.auth.application.command.LoginRequest;
import com.guidinglight.nexusquant.auth.application.result.LoginResponse;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.security.token.TokenClaims;
import com.guidinglight.nexusquant.security.token.TokenService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
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

    public DbAuthService(
            TokenService tokenService,
            PasswordEncoder passwordEncoder,
            AuthUserRepository authUserRepository,
            String issuer,
            long accessTokenTtlSeconds
    ) {
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
        this.authUserRepository = Objects.requireNonNull(authUserRepository, "authUserRepository must not be null");
        this.issuer = Objects.requireNonNull(issuer, "issuer must not be null");
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        if (!authUserRepository.hasAdminUser()) {
            throw new AdminNotInitializedException();
        }
        if (request == null || request.username() == null || request.password() == null) {
            throw new BadCredentialsException("invalid username or password");
        }
        AuthUserProfile userProfile = authUserRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("invalid username or password"));
        if (!passwordEncoder.matches(request.password(), userProfile.passwordHash())) {
            throw new BadCredentialsException("invalid username or password");
        }
        if (!userProfile.enabled()) {
            throw new DisabledException("account is disabled");
        }
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(accessTokenTtlSeconds);
        TokenClaims claims = new TokenClaims(
                userProfile.username(),
                userProfile.username(),
                userProfile.roles(),
                issuedAt,
                expiresAt,
                issuer,
                "jti-" + UUID.randomUUID()
        );
        return new LoginResponse(
                tokenService.issue(claims),
                "Bearer",
                accessTokenTtlSeconds,
                expiresAt,
                userProfile.username(),
                userProfile.roles()
        );
    }
}

