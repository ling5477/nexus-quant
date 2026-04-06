package com.guidinglight.nexusquant.auth.application;

import com.guidinglight.nexusquant.auth.application.command.LoginRequest;
import com.guidinglight.nexusquant.auth.application.result.LoginResponse;
import com.guidinglight.nexusquant.auth.domain.LocalUserAccount;
import com.guidinglight.nexusquant.security.token.JwtTokenService;
import com.guidinglight.nexusquant.security.token.TokenClaims;
import com.guidinglight.nexusquant.security.token.TokenService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * LocalAuthService 基于配置驱动本地账户提供最小可用真实登录能力。
 * <p>
 * Why:
 * Step 5 只要求后端具备真实认证基础，因此先采用本地账户 + BCrypt + JWT，
 * 为后续 GateG 登录页和当前用户接口提供真实后端基线。
 */
public class LocalAuthService implements AuthService {

    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final Map<String, LocalUserAccount> accountsByUsername;
    private final String issuer;
    private final long accessTokenTtlSeconds;

    public LocalAuthService(
            TokenService tokenService,
            PasswordEncoder passwordEncoder,
            List<LocalUserAccount> accounts,
            String issuer,
            long accessTokenTtlSeconds
    ) {
        this.tokenService = Objects.requireNonNull(tokenService, "tokenService must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
        this.accountsByUsername = Objects.requireNonNull(accounts, "accounts must not be null")
                .stream()
                .collect(Collectors.toUnmodifiableMap(LocalUserAccount::username, account -> account));
        this.issuer = Objects.requireNonNull(issuer, "issuer must not be null");
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            throw new BadCredentialsException("invalid username or password");
        }
        LocalUserAccount account = accountsByUsername.get(request.username());
        if (account == null || !passwordEncoder.matches(request.password(), account.passwordHash())) {
            throw new BadCredentialsException("invalid username or password");
        }
        if (!account.enabled()) {
            throw new DisabledException("account is disabled");
        }
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(accessTokenTtlSeconds);
        TokenClaims claims = new TokenClaims(
                account.username(),
                account.username(),
                account.roles(),
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
                account.username(),
                account.roles()
        );
    }
}


